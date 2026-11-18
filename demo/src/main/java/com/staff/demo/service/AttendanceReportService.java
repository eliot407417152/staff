package com.staff.demo.service;

import com.staff.demo.entity.AttendanceRecord;
import com.staff.demo.entity.Employee;
import com.staff.demo.entity.LeaveRequest;
import com.staff.demo.repository.AttendanceRepository;
import com.staff.demo.repository.EmployeeRepository;
import com.staff.demo.repository.LeaveRequestRepository;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** 後台「異常出勤」報表：工時不足、未打下班卡、未出勤，以及颱風假扣薪 */
@Service
@RequiredArgsConstructor
public class AttendanceReportService {

    private final EmployeeRepository employeeRepo;
    private final AttendanceRepository attendanceRepo;
    private final LeaveRequestRepository leaveRepo;
    private final WorkCalendarService calendar;

    @Getter
    public static class Row {
        private final LocalDate date;
        private final String employeeNo;
        private final String employeeName;
        private final LocalDateTime clockIn;
        private final LocalDateTime clockOut;
        private final String worked;
        private final String leave;
        private final String shortfall;
        private final String statusLabel;
        private final String statusBadge;

        Row(LocalDate date, Employee e, AttendanceRecord rec, WorkSchedule.DayResult r) {
            this.date = date;
            this.employeeNo = e.getEmployeeNo() == null ? "-" : e.getEmployeeNo();
            this.employeeName = e.getName();
            this.clockIn = rec == null ? null : rec.getClockIn();
            this.clockOut = rec == null ? null : rec.getClockOut();
            this.worked = AttendanceService.hm(r.workedMinutes());
            this.leave = AttendanceService.hm(r.leaveMinutes());
            this.shortfall = r.shortfallMinutes() > 0 ? AttendanceService.hm(r.shortfallMinutes()) : "-";
            this.statusLabel = r.status().getLabel();
            this.statusBadge = r.status().getBadge();
        }
    }

    /** 該月（到今天為止）所有異常出勤，新的日期在前 */
    public List<Row> abnormal(YearMonth ym) {
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

        LocalDateTime now = LocalDateTime.now();
        List<Row> rows = new ArrayList<>();
        for (Employee e : employeeRepo.findByEnabledTrueOrderByEmployeeNoAsc()) {
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
                if (result.status().isAbnormal()) {
                    rows.add(new Row(d, e, rec, result));
                }
            }
        }
        rows.sort(Comparator.comparing(Row::getDate).reversed().thenComparing(Row::getEmployeeNo));
        return rows;
    }
}
