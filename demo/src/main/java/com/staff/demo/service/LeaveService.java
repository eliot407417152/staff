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

import java.time.DayOfWeek;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Locale;

/** 請假申請、取消、審核 */
@Service
@RequiredArgsConstructor
public class LeaveService {

    // 上班時間：09:00–12:00、13:00–18:00（每天 8 小時），週六日不計
    private static final LocalTime AM_START = LocalTime.of(9, 0);
    private static final LocalTime AM_END = LocalTime.of(12, 0);
    private static final LocalTime PM_START = LocalTime.of(13, 0);
    private static final LocalTime PM_END = LocalTime.of(18, 0);

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

    private final LeaveRequestRepository requestRepo;
    private final LeaveTypeRepository typeRepo;
    private final EmployeeRepository employeeRepo;
    private final MailService mailService;

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

    @Transactional
    public LeaveRequest apply(String username, LeaveRequestForm f) {
        Employee e = employee(username);
        LeaveType type = typeRepo.findById(f.getLeaveTypeId()).filter(LeaveType::isActive)
                .orElseThrow(() -> new IllegalArgumentException("請選擇有效的假別"));
        LocalDateTime start = f.getStartAt();
        LocalDateTime end = f.getEndAt();

        if (!end.isAfter(start)) {
            throw new IllegalArgumentException("結束時間必須晚於開始時間");
        }
        if (ChronoUnit.DAYS.between(start.toLocalDate(), end.toLocalDate()) > 90) {
            throw new IllegalArgumentException("單次請假不可超過 90 天");
        }
        int minutes = calcMinutes(start, end);
        if (minutes <= 0) {
            throw new IllegalArgumentException("所選時段不含上班時間（週末與午休時間不計入請假）");
        }
        if (requestRepo.countOverlap(e, ACTIVE, start, end) > 0) {
            throw new IllegalArgumentException("這段時間已有審核中或已核准的請假申請");
        }
        checkQuota(e, type, start, minutes);

        LeaveRequest r = new LeaveRequest();
        r.setEmployee(e);
        r.setLeaveType(type);
        r.setStartAt(start);
        r.setEndAt(end);
        r.setMinutes(minutes);
        r.setReason(f.getReason().trim());
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

    /** 檢查額度：特休依到職日按週年計算（勞基法第 38 條），其他假別依年度上限 */
    private void checkQuota(Employee e, LeaveType type, LocalDateTime start, int minutes) {
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
                return;
            }
            quota = type.getAnnualQuotaMinutes();
            from = LocalDate.of(start.getYear(), 1, 1).atStartOfDay();
            to = from.plusYears(1);
            scope = type.getName() + "年度";
        }
        long used = requestRepo.sumMinutes(e, type, ACTIVE, from, to);
        if (used + minutes > quota) {
            throw new IllegalArgumentException(String.format(Locale.ROOT,
                    "%s額度 %s 小時，已使用或審核中 %s 小時，本次申請 %s 小時，超過額度",
                    scope, fmt(quota), fmt(used), fmt(minutes)));
        }
    }

    private static boolean isAnnual(LeaveType t) {
        return ANNUAL_CODE.equals(t.getCode());
    }

    // ===================== 工具 =====================

    /** 計算實際請假分鐘數：只算上班時段，排除午休與週六日（國定假日目前不會自動排除） */
    public static int calcMinutes(LocalDateTime start, LocalDateTime end) {
        long total = 0;
        for (LocalDate d = start.toLocalDate(); !d.isAfter(end.toLocalDate()); d = d.plusDays(1)) {
            if (d.getDayOfWeek() == DayOfWeek.SATURDAY || d.getDayOfWeek() == DayOfWeek.SUNDAY) {
                continue;
            }
            total += overlap(start, end, d.atTime(AM_START), d.atTime(AM_END));
            total += overlap(start, end, d.atTime(PM_START), d.atTime(PM_END));
        }
        return (int) total;
    }

    private static long overlap(LocalDateTime s, LocalDateTime e, LocalDateTime a, LocalDateTime b) {
        LocalDateTime from = s.isAfter(a) ? s : a;
        LocalDateTime to = e.isBefore(b) ? e : b;
        return to.isAfter(from) ? Duration.between(from, to).toMinutes() : 0;
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
