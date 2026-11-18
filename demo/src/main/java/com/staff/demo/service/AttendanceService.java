package com.staff.demo.service;

import com.staff.demo.dto.AttendanceDayView;
import com.staff.demo.entity.AttendanceRecord;
import com.staff.demo.entity.Employee;
import com.staff.demo.entity.LeaveRequest;
import com.staff.demo.repository.AttendanceRepository;
import com.staff.demo.repository.EmployeeRepository;
import com.staff.demo.repository.LeaveRequestRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class AttendanceService {

    private final EmployeeRepository employeeRepo;
    private final AttendanceRepository attendanceRepo;
    private final LeaveRequestRepository leaveRepo;
    private final WorkCalendarService calendar;

    public Employee getEmployee(String username) {
        return employeeRepo.findByUsername(username).orElseThrow();
    }

    public Optional<AttendanceRecord> findToday(String username) {
        return attendanceRepo.findByEmployeeAndWorkDate(getEmployee(username), LocalDate.now());
    }

    public List<AttendanceRecord> recent(String username) {
        return attendanceRepo.findTop10ByEmployeeOrderByWorkDateDesc(getEmployee(username));
    }

    public List<AttendanceRecord> todayAll() {
        return attendanceRepo.findByWorkDateOrderByClockInAsc(LocalDate.now());
    }

    // ===================== 工時計算（彈性上班、上滿 8 小時） =====================

    /** 今天的出勤狀況（含今天最早可下班時間）；今天不是上班日也會回傳 */
    public AttendanceDayView todayView(String username) {
        Employee e = getEmployee(username);
        LocalDate today = LocalDate.now();
        AttendanceRecord rec = attendanceRepo.findByEmployeeAndWorkDate(e, today).orElse(null);
        return views(e, List.of(today), rec == null ? List.of() : List.of(rec)).get(0);
    }

    /** 最近 10 筆打卡紀錄，附上工時與狀態 */
    public List<AttendanceDayView> recentViews(String username) {
        Employee e = getEmployee(username);
        List<AttendanceRecord> records = attendanceRepo.findTop10ByEmployeeOrderByWorkDateDesc(e);
        if (records.isEmpty()) {
            return List.of();
        }
        List<LocalDate> dates = records.stream().map(AttendanceRecord::getWorkDate).toList();
        return views(e, dates, records);
    }

    /** 打卡頁最上方的提醒文字：颱風假、放假日，或今天的上班規則 */
    public String todayNotice() {
        LocalDate today = LocalDate.now();
        WorkCalendarService.Days days = calendar.days(today, today);
        if (days.isTyphoon(today)) {
            return "今天是颱風假（停班），不需要打卡。颱風假不支薪；若要用特休補上，請到「請假」頁申請，"
                    + "沒有補上的會視為天災假並扣薪。";
        }
        String off = days.offReason(today);
        if (off != null) {
            return "今天是放假日（" + off + "），不需要上班。";
        }
        return "彈性上班 08:00–09:00（標準 08:30–17:30，午休 12:00–13:00），不論幾點到，每天都要上滿 8 小時。";
    }

    private List<AttendanceDayView> views(Employee e, List<LocalDate> dates, List<AttendanceRecord> records) {
        LocalDate from = dates.stream().min(LocalDate::compareTo).orElseThrow();
        LocalDate to = dates.stream().max(LocalDate::compareTo).orElseThrow();
        WorkCalendarService.Days days = calendar.days(from, to);
        List<LeaveRequest> leaves = leaveRepo.findOverlapping(e, List.of(LeaveRequest.Status.APPROVED),
                from.atStartOfDay(), to.plusDays(1).atStartOfDay());
        LocalDateTime now = LocalDateTime.now();
        List<AttendanceDayView> out = new ArrayList<>();
        for (LocalDate d : dates) {
            AttendanceRecord rec = records.stream().filter(r -> r.getWorkDate().equals(d)).findFirst().orElse(null);
            out.add(build(d, rec, leaves, days, now));
        }
        return out;
    }

    private static AttendanceDayView build(LocalDate date, AttendanceRecord rec, List<LeaveRequest> leaves,
                                           WorkCalendarService.Days days, LocalDateTime now) {
        LocalDateTime in = rec == null ? null : rec.getClockIn();
        LocalDateTime out = rec == null ? null : rec.getClockOut();
        if (!days.isWorkday(date)) {
            String reason = days.offReason(date);
            return new AttendanceDayView(date, in, out, "-", "-", null,
                    WorkSchedule.Status.NON_WORKDAY.getLabel() + (reason == null ? "" : "（" + reason + "）"),
                    WorkSchedule.Status.NON_WORKDAY.getBadge(), null);
        }
        List<int[]> segs = new ArrayList<>();
        for (LeaveRequest r : leaves) {
            segs.addAll(WorkSchedule.leaveSegments(date, r.getStartAt(), r.getEndAt()));
        }
        WorkSchedule.DayResult r = WorkSchedule.evaluate(date, in, out, segs, now);
        return new AttendanceDayView(date, in, out, hm(r.workedMinutes()), hm(r.leaveMinutes()),
                r.shortfallMinutes() > 0 ? hm(r.shortfallMinutes()) : null,
                r.status().getLabel(), r.status().getBadge(), r.earliestOut());
    }

    /** 分鐘轉成 H:mm */
    static String hm(int minutes) {
        return String.format(Locale.ROOT, "%d:%02d", minutes / 60, minutes % 60);
    }

    /** 回傳要顯示給使用者的訊息 */
    @Transactional
    public String clockIn(String username) {
        Employee employee = getEmployee(username);
        LocalDate today = LocalDate.now();
        if (attendanceRepo.findByEmployeeAndWorkDate(employee, today).isPresent()) {
            return "今天已經打過上班卡了。";
        }
        AttendanceRecord record = new AttendanceRecord();
        record.setEmployee(employee);
        record.setWorkDate(today);
        record.setClockIn(LocalDateTime.now());
        attendanceRepo.save(record);
        return "上班打卡成功。";
    }

    @Transactional
    public String clockOut(String username) {
        Optional<AttendanceRecord> found = findToday(username);
        if (found.isEmpty()) {
            return "請先打上班卡。";
        }
        AttendanceRecord record = found.get();
        if (record.getClockOut() != null) {
            return "今天已經打過下班卡了。";
        }
        record.setClockOut(LocalDateTime.now());
        attendanceRepo.save(record);
        return "下班打卡成功。";
    }
}
