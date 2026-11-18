package com.staff.demo.service;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.function.Predicate;

/**
 * 公司上下班規則（純計算，不依賴 Spring / 資料庫，方便單元測試）。
 * <p>
 * 標準上班 08:30–17:30，午休 12:00–13:00，彈性上班 08:00–09:00（可前後半小時），
 * 不論幾點到，每天都要「實際上班滿 8 小時」；請假時數可以抵扣。
 */
public final class WorkSchedule {

    private WorkSchedule() {}

    public static final LocalTime STANDARD_START = LocalTime.of(8, 30);
    public static final LocalTime STANDARD_END = LocalTime.of(17, 30);
    public static final LocalTime FLEX_EARLIEST = LocalTime.of(8, 0);
    public static final LocalTime FLEX_LATEST = LocalTime.of(9, 0);
    public static final LocalTime LUNCH_START = LocalTime.of(12, 0);
    public static final LocalTime LUNCH_END = LocalTime.of(13, 0);
    public static final int REQUIRED_MINUTES = 8 * 60;

    private static final int LUNCH_FROM = minuteOf(LUNCH_START);
    private static final int LUNCH_TO = minuteOf(LUNCH_END);
    private static final int FLEX_EARLIEST_MIN = minuteOf(FLEX_EARLIEST);

    /** 標準上班時段（以當天 0 點起算的分鐘）：08:30–12:00、13:00–17:30，合計 480 分鐘 */
    private static final int[][] SEGMENTS = {
            {minuteOf(STANDARD_START), LUNCH_FROM},
            {LUNCH_TO, minuteOf(STANDARD_END)}
    };

    public enum Status {
        NORMAL("正常", "s-approved"),
        ON_LEAVE("請假", "s-cancelled"),
        NOT_YET("尚未打卡", "s-cancelled"),
        IN_PROGRESS("上班中", "s-pending"),
        SHORT("工時不足", "s-rejected"),
        MISSING_OUT("未打下班卡", "s-rejected"),
        ABSENT("未出勤", "s-rejected"),
        NON_WORKDAY("非上班日", "s-cancelled");

        private final String label;
        private final String badge;

        Status(String label, String badge) {
            this.label = label;
            this.badge = badge;
        }

        public String getLabel() { return label; }

        public String getBadge() { return badge; }

        /** 屬於需要人資關注的異常 */
        public boolean isAbnormal() {
            return this == SHORT || this == MISSING_OUT || this == ABSENT;
        }
    }

    /** 單日出勤評估結果。earliestOut 只有「上班中」才有值：今天最早可下班的時間。 */
    public record DayResult(Status status, int workedMinutes, int leaveMinutes,
                            int shortfallMinutes, LocalTime earliestOut) {}

    // ===================== 請假時數 =====================

    /** 請假區間內的實際請假分鐘數：只算上班時段，排除午休、非上班日（週末、國定假日、颱風假）。 */
    public static int leaveMinutes(LocalDateTime start, LocalDateTime end, Predicate<LocalDate> workday) {
        long total = 0;
        for (LocalDate d = start.toLocalDate(); !d.isAfter(end.toLocalDate()); d = d.plusDays(1)) {
            if (!workday.test(d)) {
                continue;
            }
            for (int[] seg : SEGMENTS) {
                total += overlap(start, end, d.atStartOfDay().plusMinutes(seg[0]), d.atStartOfDay().plusMinutes(seg[1]));
            }
        }
        return (int) total;
    }

    /** 某一天內，請假區間落在上班時段的部分（分鐘，以當天 0 點起算）。 */
    public static List<int[]> leaveSegments(LocalDate date, LocalDateTime start, LocalDateTime end) {
        LocalDateTime dayStart = date.atStartOfDay();
        LocalDateTime dayEnd = date.plusDays(1).atStartOfDay();
        LocalDateTime s = start.isAfter(dayStart) ? start : dayStart;
        LocalDateTime e = end.isBefore(dayEnd) ? end : dayEnd;
        List<int[]> out = new ArrayList<>();
        if (!e.isAfter(s)) {
            return out;
        }
        int sm = (int) Duration.between(dayStart, s).toMinutes();
        int em = (int) Duration.between(dayStart, e).toMinutes();
        for (int[] seg : SEGMENTS) {
            int a = Math.max(sm, seg[0]);
            int b = Math.min(em, seg[1]);
            if (b > a) {
                out.add(new int[]{a, b});
            }
        }
        return out;
    }

    // ===================== 出勤工時 =====================

    /**
     * 評估某個上班日的出勤。
     *
     * @param clockIn   上班打卡（沒有就是 null）
     * @param clockOut  下班打卡（沒有就是 null）
     * @param leaveSegs 當天「已核准」請假落在上班時段的區間，見 {@link #leaveSegments}
     * @param now       現在時間：用來判斷這天是否還沒結束（今天沒打下班卡不算異常）
     */
    public static DayResult evaluate(LocalDate date, LocalDateTime clockIn, LocalDateTime clockOut,
                                     List<int[]> leaveSegs, LocalDateTime now) {
        List<int[]> leave = merge(leaveSegs);
        int leaveMin = total(leave);
        boolean open = !date.isBefore(now.toLocalDate());

        // 整天請假（已核准）：就算當天還是有打卡，也不算異常，一律視為「請假」
        if (leaveMin >= REQUIRED_MINUTES) {
            int worked = 0;
            if (clockIn != null && clockOut != null) {
                int inMin = Math.max(minutesFromDayStart(date, clockIn), FLEX_EARLIEST_MIN);
                worked = counted(inMin, minutesFromDayStart(date, clockOut), leave);
            }
            return new DayResult(Status.ON_LEAVE, worked, leaveMin, 0, null);
        }

        if (clockIn == null) {
            return open
                    ? new DayResult(Status.NOT_YET, 0, leaveMin, 0, null)
                    : new DayResult(Status.ABSENT, 0, leaveMin, REQUIRED_MINUTES - leaveMin, null);
        }

        int in = Math.max(minutesFromDayStart(date, clockIn), FLEX_EARLIEST_MIN);

        if (clockOut == null) {
            if (open) {
                int soFar = counted(in, minutesFromDayStart(date, now), leave);
                return new DayResult(Status.IN_PROGRESS, soFar, leaveMin, 0, earliestOut(in, leave, leaveMin));
            }
            // 過去的日子沒打下班卡：如果請假一路請到下班時間（例如下午請假直接離開），
            // 就以「請假開始的時間」當作下班時間計算，不算漏打卡
            Integer leaveStart = trailingLeaveStart(leave);
            if (leaveStart != null) {
                int worked = counted(in, leaveStart, leave);
                int shortfall = Math.max(0, REQUIRED_MINUTES - worked - leaveMin);
                return new DayResult(shortfall == 0 ? Status.NORMAL : Status.SHORT, worked, leaveMin, shortfall, null);
            }
            return new DayResult(Status.MISSING_OUT, 0, leaveMin, 0, null);
        }

        int worked = counted(in, minutesFromDayStart(date, clockOut), leave);
        int shortfall = Math.max(0, REQUIRED_MINUTES - worked - leaveMin);
        return new DayResult(shortfall == 0 ? Status.NORMAL : Status.SHORT, worked, leaveMin, shortfall, null);
    }

    /** 請假是否一路請到標準下班時間；是的話回傳該段請假的開始分鐘，否則 null。 */
    static Integer trailingLeaveStart(List<int[]> leave) {
        if (leave.isEmpty()) {
            return null;
        }
        int[] last = leave.get(leave.size() - 1);
        return last[1] >= minuteOf(STANDARD_END) ? last[0] : null;
    }

    /** 從 in 開始，扣掉午休與請假時段後，要到幾點才湊滿 8 小時（含請假時數）。算不出來回傳 null。 */
    static LocalTime earliestOut(int in, List<int[]> leave, int leaveMin) {
        for (int t = in; t <= 24 * 60; t++) {
            if (counted(in, t, leave) + leaveMin >= REQUIRED_MINUTES) {
                return t >= 24 * 60 ? null : LocalTime.of(t / 60, t % 60);
            }
        }
        return null;
    }

    /** [in, out) 之間實際上班分鐘數：扣掉午休與已核准請假。 */
    static int counted(int in, int out, List<int[]> leave) {
        if (out <= in) {
            return 0;
        }
        List<int[]> parts = new ArrayList<>();
        parts.add(new int[]{in, out});
        parts = subtract(parts, LUNCH_FROM, LUNCH_TO);
        for (int[] l : leave) {
            parts = subtract(parts, l[0], l[1]);
        }
        return total(parts);
    }

    // ===================== 小工具 =====================

    private static List<int[]> subtract(List<int[]> src, int a, int b) {
        List<int[]> out = new ArrayList<>();
        for (int[] s : src) {
            if (b <= s[0] || a >= s[1]) {
                out.add(s);
                continue;
            }
            if (a > s[0]) {
                out.add(new int[]{s[0], a});
            }
            if (b < s[1]) {
                out.add(new int[]{b, s[1]});
            }
        }
        return out;
    }

    private static List<int[]> merge(List<int[]> segs) {
        List<int[]> sorted = new ArrayList<>(segs);
        sorted.sort(Comparator.comparingInt(x -> x[0]));
        List<int[]> out = new ArrayList<>();
        for (int[] s : sorted) {
            if (!out.isEmpty() && s[0] <= out.get(out.size() - 1)[1]) {
                int[] last = out.get(out.size() - 1);
                last[1] = Math.max(last[1], s[1]);
            } else {
                out.add(new int[]{s[0], s[1]});
            }
        }
        return out;
    }

    private static int total(List<int[]> segs) {
        int sum = 0;
        for (int[] s : segs) {
            sum += s[1] - s[0];
        }
        return sum;
    }

    private static int minutesFromDayStart(LocalDate date, LocalDateTime t) {
        return (int) Duration.between(date.atStartOfDay(), t).toMinutes();
    }

    private static int minuteOf(LocalTime t) {
        return t.getHour() * 60 + t.getMinute();
    }

    private static long overlap(LocalDateTime s, LocalDateTime e, LocalDateTime a, LocalDateTime b) {
        LocalDateTime from = s.isAfter(a) ? s : a;
        LocalDateTime to = e.isBefore(b) ? e : b;
        return to.isAfter(from) ? Duration.between(from, to).toMinutes() : 0;
    }
}
