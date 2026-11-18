package com.staff.demo.service;

import com.staff.demo.entity.Employee;
import com.staff.demo.entity.LeaveRequest;
import com.staff.demo.entity.TyphoonDay;
import com.staff.demo.repository.EmployeeRepository;
import com.staff.demo.repository.LeaveRequestRepository;
import com.staff.demo.repository.TyphoonDayRepository;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 颱風假處理狀況。公司規定颱風假不支薪，算異常出勤：
 * 員工用特休補上就照常給薪；沒有補上的視為天災假，要扣薪（每天 8 小時）。
 */
@Service
@RequiredArgsConstructor
public class TyphoonService {

    private static final List<LeaveRequest.Status> ACTIVE =
            List.of(LeaveRequest.Status.PENDING, LeaveRequest.Status.APPROVED);

    private final TyphoonDayRepository typhoonRepo;
    private final EmployeeRepository employeeRepo;
    private final LeaveRequestRepository requestRepo;

    public enum Handling {
        ANNUAL_APPROVED("已用特休補上（照常給薪）", "s-approved"),
        ANNUAL_PENDING("特休審核中", "s-pending"),
        OTHER_LEAVE("已有其他請假（請人資確認）", "s-cancelled"),
        DISASTER("天災假（不支薪，扣薪）", "s-rejected");

        private final String label;
        private final String badge;

        Handling(String label, String badge) {
            this.label = label;
            this.badge = badge;
        }

        public String getLabel() { return label; }

        public String getBadge() { return badge; }
    }

    /** 員工在某個颱風假的處理狀況 */
    @Getter
    public static class Row {
        private final LocalDate date;
        private final String note;
        private final Handling handling;

        Row(LocalDate date, String note, Handling handling) {
            this.date = date;
            this.note = note == null ? "" : note;
            this.handling = handling;
        }

        public int getDeductMinutes() {
            return handling == Handling.DISASTER ? WorkSchedule.REQUIRED_MINUTES : 0;
        }

        public String getDeductHours() {
            return String.format(Locale.ROOT, "%.1f", getDeductMinutes() / 60.0);
        }

        public String getHandlingLabel() { return handling.getLabel(); }

        public String getHandlingBadge() { return handling.getBadge(); }

        /** 還沒處理，員工可以按「用特休補上」 */
        public boolean isCoverable() { return handling == Handling.DISASTER; }
    }

    /** 後台報表：某員工在某個颱風假的處理狀況 */
    @Getter
    public static class Line {
        private final String employeeNo;
        private final String employeeName;
        private final Row row;

        Line(String employeeNo, String employeeName, Row row) {
            this.employeeNo = employeeNo;
            this.employeeName = employeeName;
            this.row = row;
        }
    }

    /** 後台報表：每位員工本期要扣薪的颱風假天數與時數 */
    @Getter
    public static class Summary {
        private final String employeeNo;
        private final String employeeName;
        private int days;
        private int deductMinutes;

        Summary(String employeeNo, String employeeName) {
            this.employeeNo = employeeNo;
            this.employeeName = employeeName;
        }

        public String getDeductHours() {
            return String.format(Locale.ROOT, "%.1f", deductMinutes / 60.0);
        }
    }

    // ===================== 員工自己的颱風假 =====================

    public List<Row> forEmployee(String username, LocalDate from, LocalDate to) {
        Employee e = employeeRepo.findByUsername(username).orElseThrow();
        List<Row> rows = new ArrayList<>();
        List<LeaveRequest> leaves = leavesOf(e, from, to);
        for (TyphoonDay t : typhoonRepo.findByDateBetweenOrderByDateDesc(from, to)) {
            if (applies(e, t.getDate())) {
                rows.add(new Row(t.getDate(), t.getNote(), classify(t.getDate(), leaves)));
            }
        }
        return rows;
    }

    // ===================== 後台報表 =====================

    public List<Line> lines(LocalDate from, LocalDate to) {
        List<TyphoonDay> days = typhoonRepo.findByDateBetweenOrderByDateDesc(from, to);
        if (days.isEmpty()) {
            return List.of();
        }
        Map<Long, List<LeaveRequest>> leavesByEmployee = new LinkedHashMap<>();
        for (LeaveRequest r : requestRepo.findAllOverlapping(ACTIVE, from.atStartOfDay(), to.plusDays(1).atStartOfDay())) {
            leavesByEmployee.computeIfAbsent(r.getEmployee().getId(), k -> new ArrayList<>()).add(r);
        }
        List<Line> out = new ArrayList<>();
        for (TyphoonDay t : days) {
            for (Employee e : employeeRepo.findByEnabledTrueOrderByEmployeeNoAsc()) {
                if (applies(e, t.getDate())) {
                    List<LeaveRequest> leaves = leavesByEmployee.getOrDefault(e.getId(), List.of());
                    out.add(new Line(e.getEmployeeNo() == null ? "-" : e.getEmployeeNo(), e.getName(),
                            new Row(t.getDate(), t.getNote(), classify(t.getDate(), leaves))));
                }
            }
        }
        out.sort(Comparator.comparing((Line l) -> l.getRow().getDate()).reversed()
                .thenComparing(Line::getEmployeeNo));
        return out;
    }

    /** 每位有被扣薪颱風假的員工合計 */
    public List<Summary> summaries(List<Line> lines) {
        Map<String, Summary> map = new LinkedHashMap<>();
        for (Line l : lines) {
            if (l.getRow().getDeductMinutes() > 0) {
                Summary s = map.computeIfAbsent(l.getEmployeeNo(), k -> new Summary(l.getEmployeeNo(), l.getEmployeeName()));
                s.days++;
                s.deductMinutes += l.getRow().getDeductMinutes();
            }
        }
        return map.values().stream().sorted(Comparator.comparing(Summary::getEmployeeNo)).toList();
    }

    // ===================== 內部 =====================

    private List<LeaveRequest> leavesOf(Employee e, LocalDate from, LocalDate to) {
        return requestRepo.findOverlapping(e, ACTIVE, from.atStartOfDay(), to.plusDays(1).atStartOfDay());
    }

    /** 到職日之後才需要處理 */
    private static boolean applies(Employee e, LocalDate date) {
        return e.getHireDate() == null || !e.getHireDate().isAfter(date);
    }

    /** 這個請假是不是「專門用特休補颱風假」的那種單日整天請假 */
    static boolean isCover(LeaveRequest r, LocalDate date) {
        return "ANNUAL".equals(r.getLeaveType().getCode())
                && r.getStartAt().toLocalDate().equals(date)
                && r.getEndAt().toLocalDate().equals(date)
                && r.getMinutes() >= WorkSchedule.REQUIRED_MINUTES;
    }

    static Handling classify(LocalDate date, List<LeaveRequest> leaves) {
        LocalDateTime dayStart = date.atStartOfDay();
        LocalDateTime dayEnd = date.plusDays(1).atStartOfDay();
        Handling result = Handling.DISASTER;
        for (LeaveRequest r : leaves) {
            if (!(r.getStartAt().isBefore(dayEnd) && r.getEndAt().isAfter(dayStart))) {
                continue;
            }
            if (isCover(r, date)) {
                if (r.getStatus() == LeaveRequest.Status.APPROVED) {
                    return Handling.ANNUAL_APPROVED;
                }
                result = Handling.ANNUAL_PENDING;
            } else if (result == Handling.DISASTER) {
                result = Handling.OTHER_LEAVE;
            }
        }
        return result;
    }
}
