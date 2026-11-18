package com.staff.demo.service;

import com.staff.demo.dto.MakeupPunchForm;
import com.staff.demo.entity.AttendanceRecord;
import com.staff.demo.entity.Employee;
import com.staff.demo.entity.MakeupPunchRequest;
import com.staff.demo.repository.AttendanceRepository;
import com.staff.demo.repository.EmployeeRepository;
import com.staff.demo.repository.MakeupPunchRequestRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/** 補打卡：申請、取消、審核，以及管理員註銷已核准的補打卡 */
@Service
@RequiredArgsConstructor
public class MakeupPunchService {

    /** 只能補最近幾天內的打卡 */
    public static final int MAX_DAYS_BACK = 31;

    private final MakeupPunchRequestRepository requestRepo;
    private final EmployeeRepository employeeRepo;
    private final AttendanceRepository attendanceRepo;
    private final WorkCalendarService calendar;

    // ===================== 查詢 =====================

    public MakeupPunchRequest get(Long id) {
        return requestRepo.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "找不到補打卡申請"));
    }

    public List<MakeupPunchRequest> mine(String username) {
        return requestRepo.findByEmployeeOrderByWorkDateDescCreatedAtDesc(employee(username));
    }

    /** 待我審核：管理員看得到所有人的（除了自己的），主管只看自己的下屬 */
    public List<MakeupPunchRequest> pendingFor(String username) {
        Employee me = employee(username);
        if ("ADMIN".equals(me.getRole())) {
            return requestRepo.findByStatusOrderByCreatedAtAsc(MakeupPunchRequest.Status.PENDING).stream()
                    .filter(r -> !r.getEmployee().getId().equals(me.getId())).toList();
        }
        return requestRepo.findByStatusAndEmployeeManagerOrderByCreatedAtAsc(MakeupPunchRequest.Status.PENDING, me);
    }

    /** 後台：所有補打卡紀錄，可依狀態篩選 */
    public List<MakeupPunchRequest> all(MakeupPunchRequest.Status status) {
        return status == null ? requestRepo.findAllByOrderByCreatedAtDesc()
                : requestRepo.findByStatusOrderByCreatedAtDesc(status);
    }

    // ===================== 申請、取消 =====================

    @Transactional
    public MakeupPunchRequest apply(String username, MakeupPunchForm f) {
        Employee e = employee(username);
        LocalDate date = f.getWorkDate();
        LocalDate today = LocalDate.now();

        if (date.isAfter(today)) {
            throw new IllegalArgumentException("不能補未來的日期");
        }
        if (date.isBefore(today.minusDays(MAX_DAYS_BACK))) {
            throw new IllegalArgumentException("只能補最近 " + MAX_DAYS_BACK + " 天內的打卡");
        }
        if (!calendar.days(date, date).isWorkday(date)) {
            throw new IllegalArgumentException("這一天不是上班日（週末、國定假日或颱風假），不需要補打卡");
        }
        if (f.getClockIn() == null && f.getClockOut() == null) {
            throw new IllegalArgumentException("請至少填寫上班或下班其中一個時間");
        }
        if (f.getClockIn() != null && f.getClockOut() != null && !f.getClockOut().isAfter(f.getClockIn())) {
            throw new IllegalArgumentException("下班時間必須晚於上班時間");
        }
        LocalDateTime now = LocalDateTime.now();
        if ((f.getClockIn() != null && date.atTime(f.getClockIn()).isAfter(now))
                || (f.getClockOut() != null && date.atTime(f.getClockOut()).isAfter(now))) {
            throw new IllegalArgumentException("補打卡的時間不能晚於現在");
        }
        if (f.getClockIn() == null && attendanceRepo.findByEmployeeAndWorkDate(e, date).isEmpty()) {
            throw new IllegalArgumentException("這一天沒有上班打卡紀錄，請填寫上班時間");
        }
        if (requestRepo.existsByEmployeeAndWorkDateAndStatus(e, date, MakeupPunchRequest.Status.PENDING)) {
            throw new IllegalArgumentException("這一天已經有一筆審核中的補打卡申請");
        }

        MakeupPunchRequest r = new MakeupPunchRequest();
        r.setEmployee(e);
        r.setWorkDate(date);
        r.setClockInTime(f.getClockIn());
        r.setClockOutTime(f.getClockOut());
        r.setReason(f.getReason().trim());
        return requestRepo.save(r);
    }

    @Transactional
    public MakeupPunchRequest cancel(Long id, String username) {
        MakeupPunchRequest r = get(id);
        if (!r.getEmployee().getUsername().equals(username)) {
            throw new AccessDeniedException("只能取消自己的補打卡申請");
        }
        if (r.getStatus() != MakeupPunchRequest.Status.PENDING) {
            throw new IllegalArgumentException("只有審核中的申請可以取消");
        }
        r.setStatus(MakeupPunchRequest.Status.CANCELLED);
        return requestRepo.save(r);
    }

    // ===================== 審核 =====================

    /** 管理員可審核所有人；主管可審核自己的直屬下屬；任何人都不能審核自己的申請 */
    public boolean canDecide(Employee approver, MakeupPunchRequest r) {
        if (approver.getId().equals(r.getEmployee().getId())) {
            return false;
        }
        if ("ADMIN".equals(approver.getRole())) {
            return true;
        }
        Employee manager = r.getEmployee().getManager();
        return manager != null && manager.getId().equals(approver.getId());
    }

    @Transactional
    public MakeupPunchRequest decide(Long id, String approverUsername, boolean approve, String comment) {
        MakeupPunchRequest r = get(id);
        Employee approver = employee(approverUsername);
        if (!canDecide(approver, r)) {
            throw new AccessDeniedException("你沒有審核這筆申請的權限");
        }
        if (r.getStatus() != MakeupPunchRequest.Status.PENDING) {
            throw new IllegalArgumentException("這筆申請已經處理過了");
        }
        String note = (comment == null || comment.isBlank()) ? null : comment.trim();
        if (!approve && note == null) {
            throw new IllegalArgumentException("駁回時請填寫原因");
        }
        if (approve) {
            applyToAttendance(r);
        }
        r.setStatus(approve ? MakeupPunchRequest.Status.APPROVED : MakeupPunchRequest.Status.REJECTED);
        r.setDecidedBy(approver);
        r.setDecidedAt(LocalDateTime.now());
        r.setDecisionComment(note);
        return requestRepo.save(r);
    }

    /** 核准後把補的時間寫進打卡紀錄；同時記下原本的值，之後註銷才能還原 */
    private void applyToAttendance(MakeupPunchRequest r) {
        LocalDate date = r.getWorkDate();
        AttendanceRecord rec = attendanceRepo.findByEmployeeAndWorkDate(r.getEmployee(), date).orElse(null);

        LocalDateTime newIn = r.getClockInTime() != null ? date.atTime(r.getClockInTime())
                : (rec == null ? null : rec.getClockIn());
        LocalDateTime newOut = r.getClockOutTime() != null ? date.atTime(r.getClockOutTime())
                : (rec == null ? null : rec.getClockOut());
        if (newIn == null) {
            throw new IllegalArgumentException("這一天沒有上班打卡紀錄，補打卡必須包含上班時間");
        }
        if (newOut != null && !newOut.isAfter(newIn)) {
            throw new IllegalArgumentException("補打卡後下班時間不晚於上班時間，請駁回並請員工重新申請");
        }

        if (rec == null) {
            rec = new AttendanceRecord();
            rec.setEmployee(r.getEmployee());
            rec.setWorkDate(date);
            r.setRecordCreated(true);
        } else {
            r.setRecordCreated(false);
            r.setPrevClockIn(rec.getClockIn());
            r.setPrevClockOut(rec.getClockOut());
        }
        rec.setClockIn(newIn);
        rec.setClockOut(newOut);
        attendanceRepo.save(rec);
    }

    // ===================== 管理員註銷 =====================

    /** 管理員註銷已核准的補打卡：把打卡紀錄還原成補打卡之前的樣子。 */
    @Transactional
    public MakeupPunchRequest voidApproved(Long id, String adminUsername, String reason) {
        MakeupPunchRequest r = get(id);
        Employee admin = employee(adminUsername);
        if (!"ADMIN".equals(admin.getRole())) {
            throw new AccessDeniedException("只有管理員可以註銷補打卡");
        }
        if (r.getStatus() != MakeupPunchRequest.Status.APPROVED) {
            throw new IllegalArgumentException("只有已核准的補打卡可以註銷");
        }
        if (reason == null || reason.isBlank()) {
            throw new IllegalArgumentException("註銷時請填寫原因");
        }
        AttendanceRecord rec = attendanceRepo.findByEmployeeAndWorkDate(r.getEmployee(), r.getWorkDate()).orElse(null);
        if (rec != null) {
            if (r.isRecordCreated()) {
                attendanceRepo.delete(rec);
            } else {
                if (r.getClockInTime() != null && r.getPrevClockIn() != null) {
                    rec.setClockIn(r.getPrevClockIn());
                }
                if (r.getClockOutTime() != null) {
                    rec.setClockOut(r.getPrevClockOut());
                }
                attendanceRepo.save(rec);
            }
        }
        r.setStatus(MakeupPunchRequest.Status.VOIDED);
        r.setVoidedBy(admin);
        r.setVoidedAt(LocalDateTime.now());
        r.setVoidReason(reason.trim());
        return requestRepo.save(r);
    }

    private Employee employee(String username) {
        return employeeRepo.findByUsername(username).orElseThrow();
    }
}
