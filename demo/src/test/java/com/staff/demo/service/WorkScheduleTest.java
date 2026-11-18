package com.staff.demo.service;

import com.staff.demo.service.WorkSchedule.DayResult;
import com.staff.demo.service.WorkSchedule.Status;
import org.junit.jupiter.api.Test;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.function.Predicate;

import static org.junit.jupiter.api.Assertions.assertEquals;

class WorkScheduleTest {

    private static final LocalDate DAY = LocalDate.of(2026, 11, 18);          // 星期三
    private static final LocalDateTime NOW = LocalDate.of(2026, 11, 20).atTime(10, 0);
    private static final Predicate<LocalDate> WEEKDAY =
            d -> d.getDayOfWeek() != DayOfWeek.SATURDAY && d.getDayOfWeek() != DayOfWeek.SUNDAY;

    private static LocalDateTime at(int h, int m) {
        return DAY.atTime(h, m);
    }

    private static List<int[]> leave(LocalDateTime s, LocalDateTime e) {
        return WorkSchedule.leaveSegments(DAY, s, e);
    }

    private static DayResult eval(LocalDateTime in, LocalDateTime out, List<int[]> leave) {
        return WorkSchedule.evaluate(DAY, in, out, leave, NOW);
    }

    // ---------- 請假時數 ----------

    @Test
    void 請假時數只算上班時段() {
        assertEquals(480, WorkSchedule.leaveMinutes(at(8, 30), at(17, 30), WEEKDAY));
        assertEquals(480, WorkSchedule.leaveMinutes(at(0, 0), at(23, 59), WEEKDAY));
        assertEquals(120, WorkSchedule.leaveMinutes(at(8, 30), at(10, 30), WEEKDAY));
        assertEquals(60, WorkSchedule.leaveMinutes(at(16, 30), at(17, 30), WEEKDAY));
    }

    @Test
    void 請假跨午休要扣掉午休() {
        assertEquals(120, WorkSchedule.leaveMinutes(at(11, 0), at(14, 0), WEEKDAY));
        assertEquals(0, WorkSchedule.leaveMinutes(at(12, 0), at(13, 0), WEEKDAY));
    }

    @Test
    void 週末國定假日颱風假不算請假時數() {
        LocalDate sat = LocalDate.of(2026, 11, 21);
        assertEquals(0, WorkSchedule.leaveMinutes(sat.atTime(9, 0), sat.plusDays(1).atTime(18, 0), WEEKDAY));

        LocalDate typhoon = LocalDate.of(2026, 11, 19);
        Predicate<LocalDate> noTyphoon = d -> WEEKDAY.test(d) && !d.equals(typhoon);
        // 11/19（颱風）、11/20 兩天，只有 11/20 算 8 小時
        assertEquals(480, WorkSchedule.leaveMinutes(typhoon.atTime(8, 30), typhoon.plusDays(1).atTime(17, 30), noTyphoon));
    }

    // ---------- 出勤工時 ----------

    @Test
    void 彈性上班區間內只要上滿八小時就正常() {
        assertEquals(Status.NORMAL, eval(at(8, 0), at(17, 0), List.of()).status());
        assertEquals(Status.NORMAL, eval(at(8, 30), at(17, 30), List.of()).status());
        assertEquals(Status.NORMAL, eval(at(9, 0), at(18, 0), List.of()).status());
    }

    @Test
    void 晚到沒有補滿就是工時不足_補滿就正常() {
        DayResult r = eval(at(9, 0), at(17, 30), List.of());
        assertEquals(Status.SHORT, r.status());
        assertEquals(30, r.shortfallMinutes());
        assertEquals(Status.NORMAL, eval(at(9, 15), at(18, 15), List.of()).status());
    }

    @Test
    void 八點前到班從八點起算() {
        DayResult r = eval(at(7, 0), at(16, 59), List.of());
        assertEquals(Status.SHORT, r.status());
        assertEquals(1, r.shortfallMinutes());
    }

    @Test
    void 打卡跨午休要扣掉午休() {
        assertEquals(240, eval(at(8, 0), at(13, 0), List.of()).workedMinutes());
        assertEquals(210, eval(at(8, 30), at(12, 0), List.of()).workedMinutes());
    }

    @Test
    void 請一兩小時的假_打卡加請假要滿八小時() {
        DayResult r = eval(at(10, 30), at(17, 30), leave(at(8, 30), at(10, 30)));
        assertEquals(Status.NORMAL, r.status());
        assertEquals(360, r.workedMinutes());
        assertEquals(120, r.leaveMinutes());

        assertEquals(30, eval(at(10, 30), at(17, 0), leave(at(8, 30), at(10, 30))).shortfallMinutes());
        assertEquals(Status.NORMAL, eval(at(8, 30), at(16, 30), leave(at(16, 30), at(17, 30))).status());
    }

    @Test
    void 請假時段內有打卡不會重複計算() {
        DayResult r = eval(at(8, 30), at(17, 30), leave(at(11, 0), at(14, 0)));
        assertEquals(Status.NORMAL, r.status());
        assertEquals(480 - 120, r.workedMinutes());
        assertEquals(120, r.leaveMinutes());
    }

    @Test
    void 沒打卡的情況() {
        assertEquals(Status.ON_LEAVE, eval(null, null, leave(at(8, 30), at(17, 30))).status());
        DayResult absent = eval(null, null, List.of());
        assertEquals(Status.ABSENT, absent.status());
        assertEquals(480, absent.shortfallMinutes());
        assertEquals(Status.MISSING_OUT, eval(at(8, 30), null, List.of()).status());
    }

    @Test
    void 整天請假但還是有打卡_不算異常() {
        List<int[]> fullDay = leave(at(8, 30), at(17, 30));
        // 只打上班卡沒打下班卡（過去的日子）
        assertEquals(Status.ON_LEAVE, eval(at(8, 30), null, fullDay).status());
        // 有上下班卡
        DayResult r = eval(at(9, 0), at(11, 0), fullDay);
        assertEquals(Status.ON_LEAVE, r.status());
        assertEquals(0, r.shortfallMinutes());
    }

    @Test
    void 下午請假直接離開沒打下班卡_以請假開始時間當下班() {
        // 8:30 到班，14:00–17:30 請假：8:30–14:00 扣午休 = 270 分鐘，加請假 210 分鐘 = 480
        assertEquals(Status.NORMAL, eval(at(8, 30), null, leave(at(14, 0), at(17, 30))).status());
        // 9:00 才到：只有 240 + 210 = 450，差 30 分鐘
        DayResult r = eval(at(9, 0), null, leave(at(14, 0), at(17, 30)));
        assertEquals(Status.SHORT, r.status());
        assertEquals(30, r.shortfallMinutes());
        // 沒有請假就還是「未打下班卡」
        assertEquals(Status.MISSING_OUT, eval(at(8, 30), null, List.of()).status());
        // 請的是中間時段（沒請到下班），沒打下班卡仍是異常
        assertEquals(Status.MISSING_OUT, eval(at(8, 30), null, leave(at(10, 0), at(12, 0))).status());
    }

    @Test
    void 今天上班中_算出最早可下班時間() {
        LocalDate today = LocalDate.of(2026, 11, 20);
        LocalDateTime now = today.atTime(10, 0);
        assertEquals(LocalTime.of(17, 0), WorkSchedule.evaluate(today, today.atTime(8, 0), null, List.of(), now).earliestOut());
        assertEquals(LocalTime.of(17, 45), WorkSchedule.evaluate(today, today.atTime(8, 45), null, List.of(), now).earliestOut());
        assertEquals(LocalTime.of(18, 0), WorkSchedule.evaluate(today, today.atTime(9, 0), null, List.of(), now).earliestOut());

        List<int[]> amLeave = WorkSchedule.leaveSegments(today, today.atTime(8, 30), today.atTime(10, 30));
        DayResult r = WorkSchedule.evaluate(today, today.atTime(10, 30), null, amLeave, now);
        assertEquals(Status.IN_PROGRESS, r.status());
        assertEquals(LocalTime.of(17, 30), r.earliestOut());

        assertEquals(Status.NOT_YET, WorkSchedule.evaluate(today, null, null, List.of(), today.atTime(7, 0)).status());
    }
}
