package com.staff.demo.service;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.Optional;

/**
 * 特別休假天數（勞動基準法第 38 條），依到職日以「週年制」計算
 * （施行細則第 24 條：以受僱當日起算每一週年；滿六個月未滿一年者，為取得權利後六個月內）。
 *
 * <pre>
 * 滿 6 個月未滿 1 年：3 日    滿 1 年未滿 2 年：7 日    滿 2 年未滿 3 年：10 日
 * 滿 3 年未滿 5 年：14 日     滿 5 年未滿 10 年：15 日
 * 滿 10 年以上：每滿 1 年加 1 日，最多 30 日
 * </pre>
 */
public final class AnnualLeavePolicy {

    /** 一個特休週期：start（含）～ endExclusive（不含），期間內可休 days 日 */
    public record Entitlement(LocalDate start, LocalDate endExclusive, int days) {}

    private AnnualLeavePolicy() {}

    /** 已滿 years 年（years ≥ 1）可享有的特休日數 */
    public static int daysForCompletedYears(int years) {
        if (years < 1) {
            return 3;
        }
        if (years == 1) {
            return 7;
        }
        if (years == 2) {
            return 10;
        }
        if (years < 5) {
            return 14;
        }
        if (years < 10) {
            return 15;
        }
        return Math.min(30, 15 + (years - 9));
    }

    /** 某一天落在哪個特休週期。到職日未設定，或到職未滿 6 個月時回傳空值。 */
    public static Optional<Entitlement> on(LocalDate hireDate, LocalDate date) {
        if (hireDate == null || date == null) {
            return Optional.empty();
        }
        LocalDate sixMonths = hireDate.plusMonths(6);
        if (date.isBefore(sixMonths)) {
            return Optional.empty();
        }
        int years = (int) ChronoUnit.YEARS.between(hireDate, date);
        // 到職日是 2/29 這類日期時，上面的年數可能差一天，這裡校正到「date 一定落在週期內」
        while (!date.isBefore(hireDate.plusYears(years + 1L))) {
            years++;
        }
        while (years > 0 && date.isBefore(hireDate.plusYears(years))) {
            years--;
        }
        if (years < 1) {
            return Optional.of(new Entitlement(sixMonths, hireDate.plusYears(1), 3));
        }
        return Optional.of(new Entitlement(
                hireDate.plusYears(years), hireDate.plusYears(years + 1L), daysForCompletedYears(years)));
    }
}
