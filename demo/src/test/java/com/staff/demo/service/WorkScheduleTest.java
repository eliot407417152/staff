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
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

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

    // ---------- 請假：先決定時數，再選開始時間 ----------

    @Test
    void 請假開始時間只能是上班時段內的整點或半點() {
        List<LocalTime> times = WorkSchedule.leaveStartTimes();
        assertEquals(LocalTime.of(8, 30), times.get(0));
        assertEquals(LocalTime.of(17, 0), times.get(times.size() - 1));
        assertEquals(16, times.size());   // 上午 08:30–11:30 共 7 個，下午 13:00–17:00 共 9 個

        assertNull(WorkSchedule.validateLeaveStartTime(LocalTime.of(8, 30)));
        assertNull(WorkSchedule.validateLeaveStartTime(LocalTime.of(13, 0)));
        assertNotNull(WorkSchedule.validateLeaveStartTime(LocalTime.of(12, 0)));    // 午休
        assertNotNull(WorkSchedule.validateLeaveStartTime(LocalTime.of(12, 30)));
        assertNotNull(WorkSchedule.validateLeaveStartTime(LocalTime.of(8, 0)));     // 還沒上班
        assertNotNull(WorkSchedule.validateLeaveStartTime(LocalTime.of(17, 30)));   // 已經下班
        assertNotNull(WorkSchedule.validateLeaveStartTime(LocalTime.of(9, 15)));    // 不是整點或半點
        assertNotNull(WorkSchedule.validateLeaveStartTime(null));
    }

    @Test
    void 當天從開始時間起還剩多少可以請() {
        assertEquals(480, WorkSchedule.minutesLeftInDay(at(8, 30)));
        assertEquals(300, WorkSchedule.minutesLeftInDay(at(11, 30)));   // 上午 30 分 + 下午 270 分
        assertEquals(150, WorkSchedule.minutesLeftInDay(at(15, 0)));
        assertEquals(30, WorkSchedule.minutesLeftInDay(at(17, 0)));
    }

    @Test
    void 依時數與開始時間算出結束時間_自動跳過午休和非上班日() {
        LocalDate friday = LocalDate.of(2026, 11, 20);
        assertEquals(at(17, 30), WorkSchedule.leaveEnd(at(8, 30), 480, WEEKDAY));
        assertEquals(at(16, 0), WorkSchedule.leaveEnd(at(14, 0), 120, WEEKDAY));
        assertEquals(at(12, 0), WorkSchedule.leaveEnd(at(8, 30), 210, WEEKDAY));
        // 11:30 開始請 1 小時：上午 30 分鐘 + 下午 30 分鐘，要跳過午休
        assertEquals(at(13, 30), WorkSchedule.leaveEnd(at(11, 30), 60, WEEKDAY));
        // 星期五 15:00 請 4 小時：當天剩 2.5 小時，其餘 1.5 小時接到下週一 10:00
        assertEquals(LocalDate.of(2026, 11, 23).atTime(10, 0),
                WorkSchedule.leaveEnd(friday.atTime(15, 0), 240, WEEKDAY));
        // 16 小時 = 兩個整天；12 小時 = 一天 + 隔天上午到 13:30
        assertEquals(DAY.plusDays(1).atTime(17, 30), WorkSchedule.leaveEnd(at(8, 30), 960, WEEKDAY));
        assertEquals(DAY.plusDays(1).atTime(13, 30), WorkSchedule.leaveEnd(at(8, 30), 720, WEEKDAY));
        // 隔天是國定假日就跳過
        Predicate<LocalDate> thursdayOff = d -> WEEKDAY.test(d) && !d.equals(DAY.plusDays(1));
        assertEquals(DAY.plusDays(2).atTime(17, 30), WorkSchedule.leaveEnd(at(8, 30), 960, thursdayOff));
        assertNull(WorkSchedule.leaveEnd(at(8, 30), 0, WEEKDAY));
    }

    @Test
    void 算出來的結束時間_重新計算請假時數必定吻合() {
        Predicate<LocalDate> thursdayOff = d -> WEEKDAY.test(d) && !d.equals(DAY.plusDays(1));
        for (LocalTime start : WorkSchedule.leaveStartTimes()) {
            for (int minutes = 30; minutes <= 960; minutes += 30) {
                LocalDateTime s = DAY.atTime(start);
                LocalDateTime e = WorkSchedule.leaveEnd(s, minutes, thursdayOff);
                assertEquals(minutes, WorkSchedule.leaveMinutes(s, e, thursdayOff), start + " + " + minutes);
            }
        }
    }
}
