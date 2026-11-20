package com.staff.demo.service;

import com.staff.demo.dto.LeaveRequestForm;
import com.staff.demo.entity.Employee;
import com.staff.demo.entity.LeaveRequest;
import com.staff.demo.entity.LeaveType;
import com.staff.demo.repository.EmployeeRepository;
import com.staff.demo.repository.LeaveRequestRepository;
import com.staff.demo.repository.LeaveTypeRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.TextStyle;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** 請假申請、取消、審核 */
@Service
@RequiredArgsConstructor
public class LeaveService {

    // 上班時間規則（08:30–12:00、13:00–17:30，每天 8 小時）見 WorkSchedule；
    // 週六日、國定假日、颱風假不計請假時數，見 WorkCalendarService。

    private static final List<LeaveRequest.Status> ACTIVE =
            List.of(LeaveRequest.Status.PENDING, LeaveRequest.Status.APPROVED);
    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
    private static final int MINUTES_PER_DAY = 8 * 60;
    /** 假別代碼為 ANNUAL 的是特休，額度依到職日計算，不看假別上的年度上限 */
    private static final String ANNUAL_CODE = "ANNUAL";

    // ---- API 回傳用 ----
    public record LeaveView(Long id, String employeeNo, String employeeName, String leaveType,
                            LocalDateTime startAt, LocalDateTime endAt, double hours, String reason,
                            String status, String decidedBy, LocalDateTime decidedAt,
                            String decisionComment, LocalDateTime createdAt) {}

    public record TypeView(Long id, String code, String name, Double annualQuotaHours) {}

    public record BalanceView(String leaveType, String quotaHours, String approvedHours,
                              String pendingHours, String period) {}

    /** 請假試算結果：預計結束時間、時數、額度，以及要提醒申請人的事項（不寫入資料庫） */
    public record LeavePlan(LeaveType type, LocalDateTime start, LocalDateTime end, int minutes,
                            String summary, String quotaNote, String notice) {}

    private final LeaveRequestRepository requestRepo;
    private final LeaveTypeRepository typeRepo;
    private final EmployeeRepository employeeRepo;
    private final MailService mailService;
    private final WorkCalendarService calendar;

    // ===================== 查詢 =====================

    public List<LeaveType> activeTypes() {
        return typeRepo.findByActiveTrueOrderByIdAsc();
    }

    public List<TypeView> typeViews() {
        return activeTypes().stream()
                .map(t -> new TypeView(t.getId(), t.getCode(), t.getName(),
                        t.getAnnualQuotaMinutes() == null ? null : t.getAnnualQuotaMinutes() / 60.0))
                .toList();
    }

    public LeaveView toView(LeaveRequest r) {
        Employee e = r.getEmployee();
        return new LeaveView(r.getId(), e.getEmployeeNo(), e.getName(), r.getLeaveType().getName(),
                r.getStartAt(), r.getEndAt(), r.getHours(), r.getReason(), r.getStatus().name(),
                r.getDecidedBy() == null ? null : r.getDecidedBy().getName(),
                r.getDecidedAt(), r.getDecisionComment(), r.getCreatedAt());
    }

    public LeaveRequest get(Long id) {
        return requestRepo.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "找不到請假申請"));
    }

    public List<LeaveRequest> mine(String username) {
        return requestRepo.findByEmployeeOrderByStartAtDesc(employee(username));
    }

    /** 各假別的額度與使用狀況：特休依到職日按週年計算，其他假別為當年度 */
    public List<BalanceView> balances(String username) {
        Employee e = employee(username);
        LocalDate today = LocalDate.now();
        LocalDateTime yearFrom = LocalDate.of(today.getYear(), 1, 1).atStartOfDay();
        return activeTypes().stream().map(t -> {
            if (isAnnual(t)) {
                return AnnualLeavePolicy.on(e.getHireDate(), today)
                        .map(ent -> balance(e, t, fmt((long) ent.days() * MINUTES_PER_DAY),
                                ent.start().atStartOfDay(), ent.endExclusive().atStartOfDay(),
                                ent.start() + " ～ " + ent.endExclusive().minusDays(1)))
                        .orElseGet(() -> new BalanceView(t.getName(), "0.0", "0.0", "0.0",
                                e.getHireDate() == null ? "尚未設定到職日" : "到職滿 6 個月後才有特休"));
            }
            return balance(e, t, t.getAnnualQuotaMinutes() == null ? "不限" : fmt(t.getAnnualQuotaMinutes()),
                    yearFrom, yearFrom.plusYears(1), today.getYear() + " 年度");
        }).toList();
    }

    private BalanceView balance(Employee e, LeaveType t, String quota,
                                LocalDateTime from, LocalDateTime to, String period) {
        return new BalanceView(t.getName(), quota,
                fmt(requestRepo.sumMinutes(e, t, List.of(LeaveRequest.Status.APPROVED), from, to)),
                fmt(requestRepo.sumMinutes(e, t, List.of(LeaveRequest.Status.PENDING), from, to)), period);
    }

    /** 待我審核：管理員看得到所有人的（除了自己的），主管只看自己的下屬 */
    public List<LeaveRequest> pendingFor(String username) {
        Employee me = employee(username);
        if ("ADMIN".equals(me.getRole())) {
            return requestRepo.findByStatusOrderByCreatedAtAsc(LeaveRequest.Status.PENDING).stream()
                    .filter(r -> !r.getEmployee().getId().equals(me.getId())).toList();
        }
        return requestRepo.findByStatusAndEmployeeManagerOrderByCreatedAtAsc(LeaveRequest.Status.PENDING, me);
    }

    /** 後台：所有請假紀錄，可依狀態篩選 */
    public List<LeaveRequest> all(LeaveRequest.Status status) {
        return status == null ? requestRepo.findAllByOrderByCreatedAtDesc()
                : requestRepo.findByStatusOrderByCreatedAtDesc(status);
    }

    // ===================== 申請、取消 =====================

    /**
     * 請假流程：先決定假別與時數，再選開始日期與時間，由系統算出結束時間。
     * 這裡做完所有檢查但不寫入資料庫；畫面上的「試算」與正式送出走同一套規則。
     */
    @Transactional(readOnly = true)
    public LeavePlan plan(String username, LeaveRequestForm f) {
        Employee e = employee(username);
        LeaveType type = typeRepo.findById(f.getLeaveTypeId()).filter(LeaveType::isActive)
                .orElseThrow(() -> new IllegalArgumentException("請選擇有效的假別"));
        int minutes = toMinutes(f.getHours());

        String timeError = WorkSchedule.validateLeaveStartTime(f.getStartTime());
        if (timeError != null) {
            throw new IllegalArgumentException(timeError);
        }
        LocalDate date = f.getStartDate();
        if (e.getHireDate() != null && date.isBefore(e.getHireDate())) {
            throw new IllegalArgumentException("開始日期不能早於到職日（" + e.getHireDate() + "）");
        }
        WorkCalendarService.Days days = calendar.days(date, date.plusDays(120));
        if (!days.isWorkday(date)) {
            throw new IllegalArgumentException(offDayMessage(date, days));
        }
        LocalDateTime start = date.atTime(f.getStartTime());

        // 一天以內的請假要在同一天內請完，避免不小心延到隔天
        int left = WorkSchedule.minutesLeftInDay(start);
        if (minutes <= WorkSchedule.REQUIRED_MINUTES && minutes > left) {
            throw new IllegalArgumentException(String.format(Locale.ROOT,
                    "從 %s 開始，當天只剩 %s 小時可以請。請把開始時間提早，或把時數改成 %s 小時以內",
                    f.getStartTime(), fmt(left), fmt(left)));
        }
        LocalDateTime end = WorkSchedule.leaveEnd(start, minutes, days::isWorkday);
        if (end == null || ChronoUnit.DAYS.between(date, end.toLocalDate()) > 90) {
            throw new IllegalArgumentException("單次請假不可超過 90 天，請分開申請");
        }
        if (requestRepo.countOverlap(e, ACTIVE, start, end) > 0) {
            throw new IllegalArgumentException("這段時間（" + label(start) + " ～ " + label(end)
                    + "）已有審核中或已核准的請假申請");
        }
        QuotaState q = quotaState(e, type, start);
        assertWithinQuota(q, minutes);

        String quotaNote = q.quota() == null
                ? type.getName() + "不限額度"
                : String.format(Locale.ROOT, "%s額度 %s 小時，已使用或審核中 %s 小時，這筆核准後剩 %s 小時",
                        q.scope(), fmt(q.quota()), fmt(q.used()), fmt(q.quota() - q.used() - minutes));
        List<String> notes = new ArrayList<>();
        if (start.isBefore(LocalDateTime.now())) {
            notes.add("開始時間已經過了，這是事後補請假。");
        }
        if (!end.toLocalDate().equals(date)) {
            notes.add("跨日請假：第一天從開始時間算起，之後每天從 08:30 接續。");
        }
        String summary = String.format(Locale.ROOT, "%s ～ %s，共 %s 小時（已扣除午休與非上班日）",
                label(start), end.toLocalDate().equals(date) ? end.toLocalTime().toString() : label(end), fmt(minutes));
        return new LeavePlan(type, start, end, minutes, summary, quotaNote,
                notes.isEmpty() ? null : String.join(" ", notes));
    }

    @Transactional
    public LeaveRequest apply(String username, LeaveRequestForm f) {
        LeavePlan p = plan(username, f);
        if (calcMinutes(p.start(), p.end()) != p.minutes()) {
            throw new IllegalStateException("請假時數計算不一致，請聯絡管理員");
        }
        LeaveRequest r = new LeaveRequest();
        r.setEmployee(employee(username));
        r.setLeaveType(p.type());
        r.setStartAt(p.start());
        r.setEndAt(p.end());
        r.setMinutes(p.minutes());
        r.setReason(f.getReason().trim());
        requestRepo.save(r);
        notifyManager(r);
        return r;
    }

    /** 時數必須是 0.5 小時的倍數 */
    private static int toMinutes(BigDecimal hours) {
        BigDecimal m = hours.multiply(BigDecimal.valueOf(60));
        if (m.remainder(BigDecimal.valueOf(WorkSchedule.LEAVE_STEP_MINUTES)).signum() != 0) {
            throw new IllegalArgumentException("請假時數必須是 0.5 小時的倍數（例如 1、1.5、4、8）");
        }
        return m.intValueExact();
    }

    /** 開始日不是上班日時，說明原因並指出下一個上班日 */
    private String offDayMessage(LocalDate date, WorkCalendarService.Days days) {
        String reason = days.offReason(date);
        LocalDate next = date;
        for (int i = 0; i < 60; i++) {
            next = next.plusDays(1);
            if (days.isWorkday(next)) {
                break;
            }
        }
        String msg = date + "（" + dow(date) + "）是" + reason + "，不是上班日，不能當請假的開始日。"
                + "下一個上班日是 " + next + "（" + dow(next) + "）";
        return "颱風假".equals(reason) ? msg + "；颱風假請在下方「颱風假」區塊用特休補上" : msg;
    }

    private static String label(LocalDateTime t) {
        return t.toLocalDate() + "（" + dow(t.toLocalDate()) + "）" + t.toLocalTime();
    }

    private static String dow(LocalDate d) {
        return d.getDayOfWeek().getDisplayName(TextStyle.SHORT, Locale.TAIWAN);
    }

    /**
     * 颱風假（停班日）用特休補上：建立一筆該日整天（8 小時）的特休申請，仍由主管審核。
     * 沒有補上的颱風假視為天災假，不支薪。
     */
    @Transactional
    public LeaveRequest applyTyphoonCover(String username, LocalDate date) {
        Employee e = employee(username);
        if (!calendar.isTyphoonDay(date)) {
            throw new IllegalArgumentException("這一天不是颱風假");
        }
        if (e.getHireDate() != null && e.getHireDate().isAfter(date)) {
            throw new IllegalArgumentException("到職日之前的颱風假不需要處理");
        }
        LeaveType type = typeRepo.findByCode(ANNUAL_CODE).filter(LeaveType::isActive)
                .orElseThrow(() -> new IllegalArgumentException("系統找不到特休假別，請聯絡管理員"));
        LocalDateTime start = date.atTime(WorkSchedule.STANDARD_START);
        LocalDateTime end = date.atTime(WorkSchedule.STANDARD_END);
        if (requestRepo.countOverlap(e, ACTIVE, start, end) > 0) {
            throw new IllegalArgumentException("這一天已有審核中或已核准的請假申請；"
                    + "若是跨日請假，請先取消再分開申請");
        }
        checkQuota(e, type, start, WorkSchedule.REQUIRED_MINUTES);

        LeaveRequest r = new LeaveRequest();
        r.setEmployee(e);
        r.setLeaveType(type);
        r.setStartAt(start);
        r.setEndAt(end);
        r.setMinutes(WorkSchedule.REQUIRED_MINUTES);
        r.setReason("颱風假（停班日）以特休補上");
        requestRepo.save(r);
        notifyManager(r);
        return r;
    }

    @Transactional
    public LeaveRequest cancel(Long id, String username) {
        LeaveRequest r = get(id);
        if (!r.getEmployee().getUsername().equals(username)) {
            throw new AccessDeniedException("只能取消自己的請假申請");
        }
        if (!r.isCancellable()) {
            throw new IllegalArgumentException("這筆申請目前無法取消");
        }
        r.setStatus(LeaveRequest.Status.CANCELLED);
        return requestRepo.save(r);
    }

    // ===================== 審核 =====================

    /** 管理員可審核所有人；主管可審核自己的直屬下屬；任何人都不能審核自己的申請 */
    public boolean canDecide(Employee approver, LeaveRequest r) {
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
    public LeaveRequest decide(Long id, String approverUsername, boolean approve, String comment) {
        LeaveRequest r = get(id);
        Employee approver = employee(approverUsername);
        if (!canDecide(approver, r)) {
            throw new AccessDeniedException("你沒有審核這筆申請的權限");
        }
        if (r.getStatus() != LeaveRequest.Status.PENDING) {
            throw new IllegalArgumentException("這筆申請已經處理過了");
        }
        String note = (comment == null || comment.isBlank()) ? null : comment.trim();
        if (!approve && note == null) {
            throw new IllegalArgumentException("駁回時請填寫原因");
        }
        r.setStatus(approve ? LeaveRequest.Status.APPROVED : LeaveRequest.Status.REJECTED);
        r.setDecidedBy(approver);
        r.setDecidedAt(LocalDateTime.now());
        r.setDecisionComment(note);
        requestRepo.save(r);
        notifyApplicant(r);
        return r;
    }

    /** 額度狀況：quota 為 null 代表不限 */
    private record QuotaState(Long quota, long used, String scope) {}

    /** 特休依到職日按週年計算（勞基法第 38 條），其他假別依年度上限 */
    private QuotaState quotaState(Employee e, LeaveType type, LocalDateTime start) {
        long quota;
        LocalDateTime from;
        LocalDateTime to;
        String scope;
        if (isAnnual(type)) {
            if (e.getHireDate() == null) {
                throw new IllegalArgumentException("尚未設定到職日，無法計算特休，請聯絡管理員");
            }
            AnnualLeavePolicy.Entitlement ent = AnnualLeavePolicy.on(e.getHireDate(), start.toLocalDate())
                    .orElseThrow(() -> new IllegalArgumentException(
                            "特休要到職滿 6 個月（" + e.getHireDate().plusMonths(6) + "）之後才有"));
            quota = (long) ent.days() * MINUTES_PER_DAY;
            from = ent.start().atStartOfDay();
            to = ent.endExclusive().atStartOfDay();
            scope = "特休（" + ent.start() + " ～ " + ent.endExclusive().minusDays(1) + "）";
        } else {
            if (type.getAnnualQuotaMinutes() == null) {
                return new QuotaState(null, 0, type.getName());
            }
            quota = type.getAnnualQuotaMinutes();
            from = LocalDate.of(start.getYear(), 1, 1).atStartOfDay();
            to = from.plusYears(1);
            scope = type.getName() + "年度";
        }
        return new QuotaState(quota, requestRepo.sumMinutes(e, type, ACTIVE, from, to), scope);
    }

    private void assertWithinQuota(QuotaState q, int minutes) {
        if (q.quota() != null && q.used() + minutes > q.quota()) {
            throw new IllegalArgumentException(String.format(Locale.ROOT,
                    "%s額度 %s 小時，已使用或審核中 %s 小時，本次申請 %s 小時，超過額度",
                    q.scope(), fmt(q.quota()), fmt(q.used()), fmt(minutes)));
        }
    }

    private void checkQuota(Employee e, LeaveType type, LocalDateTime start, int minutes) {
        assertWithinQuota(quotaState(e, type, start), minutes);
    }

    private static boolean isAnnual(LeaveType t) {
        return ANNUAL_CODE.equals(t.getCode());
    }

    // ===================== 工具 =====================

    /**
     * 計算實際請假分鐘數：只算上班時段（08:30–12:00、13:00–17:30），
     * 排除午休，以及週六日、國定假日（辦公日曆表）、颱風假。
     */
    public int calcMinutes(LocalDateTime start, LocalDateTime end) {
        WorkCalendarService.Days days = calendar.days(start.toLocalDate(), end.toLocalDate());
        return WorkSchedule.leaveMinutes(start, end, days::isWorkday);
    }

    private static String fmt(long minutes) {
        return String.format(Locale.ROOT, "%.1f", minutes / 60.0);
    }

    private Employee employee(String username) {
        return employeeRepo.findByUsername(username).orElseThrow();
    }

    private void notifyManager(LeaveRequest r) {
        Employee manager = r.getEmployee().getManager();
        if (manager == null || manager.getEmail() == null) {
            return;
        }
        mailService.sendQuietly(manager.getEmail(),
                "【請假審核】" + r.getEmployee().getName() + " 提出請假申請",
                manager.getName() + " 您好，\n\n"
                        + r.getEmployee().getName() + " 申請" + r.getLeaveType().getName() + "：\n"
                        + r.getStartAt().format(TIME_FMT) + " ～ " + r.getEndAt().format(TIME_FMT)
                        + "（" + fmt(r.getMinutes()) + " 小時）\n"
                        + "事由：" + r.getReason() + "\n\n請登入系統審核。");
    }

    private void notifyApplicant(LeaveRequest r) {
        Employee applicant = r.getEmployee();
        if (applicant.getEmail() == null) {
            return;
        }
        boolean approved = r.getStatus() == LeaveRequest.Status.APPROVED;
        mailService.sendQuietly(applicant.getEmail(),
                "【請假結果】你的" + r.getLeaveType().getName() + (approved ? "已核准" : "被駁回"),
                applicant.getName() + " 您好，\n\n"
                        + "你申請的" + r.getLeaveType().getName() + "（"
                        + r.getStartAt().format(TIME_FMT) + " ～ " + r.getEndAt().format(TIME_FMT) + "）"
                        + (approved ? "已核准。" : "已被駁回。") + "\n"
                        + "審核人：" + r.getDecidedBy().getName() + "\n"
                        + (r.getDecisionComment() == null ? "" : "審核意見：" + r.getDecisionComment() + "\n"));
    }
}
