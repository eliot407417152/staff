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
import java.time.YearMonth;
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

    /**
     * 某個月（到今天為止）的出勤紀錄：依行事曆每一天都列出來，新的日期在前。
     * 上班日沒打卡也會顯示（尚未打卡／未出勤／請假），放假日顯示為「非上班日」。到職日之前不列。
     */
    public List<AttendanceDayView> monthViews(String username, YearMonth ym) {
        Employee e = getEmployee(username);
        LocalDate today = LocalDate.now();
        LocalDate from = ym.atDay(1);
        if (e.getHireDate() != null && e.getHireDate().isAfter(from)) {
            from = e.getHireDate();
        }
        LocalDate to = ym.atEndOfMonth().isAfter(today) ? today : ym.atEndOfMonth();
        if (to.isBefore(from)) {
            return List.of();
        }
        List<LocalDate> dates = new ArrayList<>();
        for (LocalDate d = to; !d.isBefore(from); d = d.minusDays(1)) {
            dates.add(d);
        }
        List<AttendanceRecord> records = attendanceRepo.findByEmployeeAndWorkDateBetween(e, from, to);
        return views(e, dates, records);
    }

    /** 打卡頁最上方的提醒文字：颱風假或放假日才有；一般上班日回傳 null（頁面不顯示） */
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
        return null;
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

    /**
     * 回傳要顯示給使用者的訊息。
     * 一天只留一筆紀錄（首次上班打卡～最後一次下班打卡），中間的外出／請假時段由已核准的請假扣除。
     * 已打過下班卡後再按上班打卡，視為「回來繼續上班」，會清除下班時間，等下次下班再打。
     */
    @Transactional
    public String clockIn(String username) {
        Employee employee = getEmployee(username);
        LocalDate today = LocalDate.now();
        Optional<AttendanceRecord> found = attendanceRepo.findByEmployeeAndWorkDate(employee, today);
        if (found.isPresent()) {
            AttendanceRecord record = found.get();
            if (record.getClockOut() == null) {
                return "今天已經打過上班卡了。";
            }
            record.setClockOut(null);
            attendanceRepo.save(record);
            return "已重新上班打卡（原本的下班時間已清除，請在真正下班時再打下班卡）。";
        }
        AttendanceRecord record = new AttendanceRecord();
        record.setEmployee(employee);
        record.setWorkDate(today);
        record.setClockIn(LocalDateTime.now());
        attendanceRepo.save(record);
        return "上班打卡成功。";
    }

    /** 下班卡可以重複打，以最後一次為準（例如請中間幾小時假、中途離開又回來） */
    @Transactional
    public String clockOut(String username) {
        Optional<AttendanceRecord> found = findToday(username);
        if (found.isEmpty()) {
            return "請先打上班卡。";
        }
        AttendanceRecord record = found.get();
        boolean again = record.getClockOut() != null;
        record.setClockOut(LocalDateTime.now());
        attendanceRepo.save(record);
        return again ? "已更新下班時間（以最後一次打卡為準）。" : "下班打卡成功。";
    }
}
