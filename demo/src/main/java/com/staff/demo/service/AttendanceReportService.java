package com.staff.demo.service;

import com.staff.demo.entity.AbnormalResolution;
import com.staff.demo.entity.AttendanceRecord;
import com.staff.demo.entity.Employee;
import com.staff.demo.entity.LeaveRequest;
import com.staff.demo.repository.AbnormalResolutionRepository;
import com.staff.demo.repository.AttendanceRepository;
import com.staff.demo.repository.EmployeeRepository;
import com.staff.demo.repository.LeaveRequestRepository;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 後台「異常出勤」報表：工時不足、未打下班卡、未出勤，以及颱風假扣薪。
 * <p>
 * 異常是每次依「打卡＋已核准請假＋核准的補打卡」即時計算出來的：請假核准、補打卡核准後，
 * 異常會自動消失；管理員也可以手動「註銷」（確認其實有來上班），註銷後移到「已處理」。
 */
@Service
@RequiredArgsConstructor
public class AttendanceReportService {

    /** 處理狀態篩選 */
    public enum State {
        PENDING("待處理"), RESOLVED("已註銷"), ALL("全部");

        private final String label;

        State(String label) { this.label = label; }

        public String getLabel() { return label; }
    }

    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    private final EmployeeRepository employeeRepo;
    private final AttendanceRepository attendanceRepo;
    private final LeaveRequestRepository leaveRepo;
    private final AbnormalResolutionRepository resolutionRepo;
    private final WorkCalendarService calendar;

    @Getter
    public static class Row {
        private final LocalDate date;
        private final Long employeeId;
        private final String employeeNo;
        private final String employeeName;
        private final Long departmentId;
        private final String departmentName;
        private final LocalDateTime clockIn;
        private final LocalDateTime clockOut;
        private final String worked;
        private final String leave;
        private final String shortfall;
        private final WorkSchedule.Status category;
        private final String statusLabel;
        private final String statusBadge;
        /** 已被管理員註銷時才有值 */
        private final boolean resolved;
        private final String resolvedNote;

        Row(LocalDate date, Employee e, AttendanceRecord rec, WorkSchedule.DayResult r, AbnormalResolution res) {
            this.date = date;
            this.employeeId = e.getId();
            this.employeeNo = e.getEmployeeNo() == null ? "-" : e.getEmployeeNo();
            this.employeeName = e.getName();
            this.departmentId = e.getDepartment() == null ? null : e.getDepartment().getId();
            this.departmentName = e.getDepartment() == null ? "-" : e.getDepartment().getName();
            this.clockIn = rec == null ? null : rec.getClockIn();
            this.clockOut = rec == null ? null : rec.getClockOut();
            this.worked = AttendanceService.hm(r.workedMinutes());
            this.leave = AttendanceService.hm(r.leaveMinutes());
            this.shortfall = r.shortfallMinutes() > 0 ? AttendanceService.hm(r.shortfallMinutes()) : "-";
            this.category = r.status();
            this.statusLabel = r.status().getLabel();
            this.statusBadge = r.status().getBadge();
            this.resolved = res != null;
            this.resolvedNote = res == null ? null
                    : res.getResolvedBy().getName() + " 於 " + res.getResolvedAt().format(TIME_FMT)
                    + " 註銷：" + res.getReason();
        }

        /** 分類代碼，供表單使用 */
        public String getCategoryCode() { return category.name(); }
    }

    /** 該月（到今天為止）所有異常出勤，新的日期在前（待處理＋已註銷） */
    public List<Row> abnormal(YearMonth ym) {
        return abnormal(ym, null, null, State.ALL);
    }

    /**
     * 該月（到今天為止）的異常出勤。
     *
     * @param departmentId 只看某部門；null＝全部部門
     * @param category     只看某種異常（工時不足、未打下班卡、未出勤）；null＝全部分類
     * @param state        待處理／已註銷／全部
     */
    public List<Row> abnormal(YearMonth ym, Long departmentId, WorkSchedule.Status category, State state) {
        LocalDate today = LocalDate.now();
        LocalDate from = ym.atDay(1);
        LocalDate to = ym.atEndOfMonth().isAfter(today) ? today : ym.atEndOfMonth();
        if (to.isBefore(from)) {
            return List.of();
        }
        WorkCalendarService.Days days = calendar.days(from, to);

        Map<String, AttendanceRecord> records = new HashMap<>();
        for (AttendanceRecord r : attendanceRepo.findByWorkDateBetween(from, to)) {
            records.put(r.getEmployee().getId() + "|" + r.getWorkDate(), r);
        }
        Map<Long, List<LeaveRequest>> leaves = new HashMap<>();
        for (LeaveRequest r : leaveRepo.findAllOverlapping(List.of(LeaveRequest.Status.APPROVED),
                from.atStartOfDay(), to.plusDays(1).atStartOfDay())) {
            leaves.computeIfAbsent(r.getEmployee().getId(), k -> new ArrayList<>()).add(r);
        }
        Map<String, AbnormalResolution> resolutions = new HashMap<>();
        for (AbnormalResolution r : resolutionRepo.findByWorkDateBetweenAndActiveTrue(from, to)) {
            resolutions.put(r.getEmployee().getId() + "|" + r.getWorkDate(), r);
        }

        LocalDateTime now = LocalDateTime.now();
        List<Row> rows = new ArrayList<>();
        for (Employee e : employeeRepo.findByEnabledTrueOrderByEmployeeNoAsc()) {
            if (departmentId != null
                    && (e.getDepartment() == null || !departmentId.equals(e.getDepartment().getId()))) {
                continue;
            }
            List<LeaveRequest> mine = leaves.getOrDefault(e.getId(), List.of());
            for (LocalDate d = from; !d.isAfter(to); d = d.plusDays(1)) {
                if (!days.isWorkday(d) || (e.getHireDate() != null && d.isBefore(e.getHireDate()))) {
                    continue;
                }
                List<int[]> segs = new ArrayList<>();
                for (LeaveRequest r : mine) {
                    segs.addAll(WorkSchedule.leaveSegments(d, r.getStartAt(), r.getEndAt()));
                }
                AttendanceRecord rec = records.get(e.getId() + "|" + d);
                WorkSchedule.DayResult result = WorkSchedule.evaluate(d,
                        rec == null ? null : rec.getClockIn(), rec == null ? null : rec.getClockOut(), segs, now);
                if (!result.status().isAbnormal()) {
                    continue;
                }
                if (category != null && result.status() != category) {
                    continue;
                }
                AbnormalResolution res = resolutions.get(e.getId() + "|" + d);
                if (state == State.PENDING && res != null) {
                    continue;
                }
                if (state == State.RESOLVED && res == null) {
                    continue;
                }
                rows.add(new Row(d, e, rec, result, res));
            }
        }
        rows.sort(Comparator.comparing(Row::getDate).reversed().thenComparing(Row::getEmployeeNo));
        return rows;
    }

    // ===================== 管理員處理 =====================

    /** 註銷異常：管理員確認這筆異常不成立（例如其實有來上班）。原始打卡資料不會被改動。 */
    @Transactional
    public void resolve(String adminUsername, Long employeeId, LocalDate date, String reason) {
        Employee admin = employeeRepo.findByUsername(adminUsername).orElseThrow();
        Employee target = employeeRepo.findById(employeeId)
                .orElseThrow(() -> new IllegalArgumentException("找不到員工"));
        if (reason == null || reason.isBlank()) {
            throw new IllegalArgumentException("註銷異常時請填寫原因");
        }
        if (reason.trim().length() > 200) {
            throw new IllegalArgumentException("原因最多 200 字");
        }
        AbnormalResolution res = resolutionRepo.findByEmployeeAndWorkDate(target, date).orElseGet(() -> {
            AbnormalResolution n = new AbnormalResolution();
            n.setEmployee(target);
            n.setWorkDate(date);
            return n;
        });
        res.setReason(reason.trim());
        res.setResolvedBy(admin);
        res.setResolvedAt(LocalDateTime.now());
        res.setActive(true);
        res.setRevokedBy(null);
        res.setRevokedAt(null);
        resolutionRepo.save(res);
    }

    /** 撤銷註銷：這筆異常重新回到「待處理」 */
    @Transactional
    public void revoke(String adminUsername, Long employeeId, LocalDate date) {
        Employee admin = employeeRepo.findByUsername(adminUsername).orElseThrow();
        Employee target = employeeRepo.findById(employeeId)
                .orElseThrow(() -> new IllegalArgumentException("找不到員工"));
        AbnormalResolution res = resolutionRepo.findByEmployeeAndWorkDate(target, date)
                .filter(AbnormalResolution::isActive)
                .orElseThrow(() -> new IllegalArgumentException("這筆異常沒有註銷紀錄"));
        res.setActive(false);
        res.setRevokedBy(admin);
        res.setRevokedAt(LocalDateTime.now());
        resolutionRepo.save(res);
    }
}
