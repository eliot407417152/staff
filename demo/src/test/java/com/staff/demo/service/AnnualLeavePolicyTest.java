package com.staff.demo.service;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AnnualLeavePolicyTest {

    private static LocalDate d(int y, int m, int day) {
        return LocalDate.of(y, m, day);
    }

    @Test
    void 勞基法第38條天數表() {
        assertEquals(7, AnnualLeavePolicy.daysForCompletedYears(1));
        assertEquals(10, AnnualLeavePolicy.daysForCompletedYears(2));
        assertEquals(14, AnnualLeavePolicy.daysForCompletedYears(3));
        assertEquals(14, AnnualLeavePolicy.daysForCompletedYears(4));
        assertEquals(15, AnnualLeavePolicy.daysForCompletedYears(5));
        assertEquals(15, AnnualLeavePolicy.daysForCompletedYears(9));
        assertEquals(16, AnnualLeavePolicy.daysForCompletedYears(10));
        assertEquals(17, AnnualLeavePolicy.daysForCompletedYears(11));
        assertEquals(30, AnnualLeavePolicy.daysForCompletedYears(24));
        assertEquals(30, AnnualLeavePolicy.daysForCompletedYears(40));
    }

    @Test
    void 未滿六個月或沒有到職日沒有特休() {
        assertTrue(AnnualLeavePolicy.on(d(2026, 3, 1), d(2026, 8, 31)).isEmpty());
        assertTrue(AnnualLeavePolicy.on(null, d(2026, 8, 31)).isEmpty());
    }

    @Test
    void 滿六個月給三天_期間到滿一年前一天() {
        var e = AnnualLeavePolicy.on(d(2026, 3, 1), d(2026, 9, 1)).orElseThrow();
        assertEquals(3, e.days());
        assertEquals(d(2026, 9, 1), e.start());
        assertEquals(d(2027, 3, 1), e.endExclusive());
    }

    @Test
    void 滿一年給七天_滿兩年給十天() {
        var one = AnnualLeavePolicy.on(d(2026, 3, 1), d(2027, 3, 1)).orElseThrow();
        assertEquals(7, one.days());
        assertEquals(d(2027, 3, 1), one.start());
        assertEquals(d(2028, 3, 1), one.endExclusive());

        var two = AnnualLeavePolicy.on(d(2026, 3, 1), d(2028, 6, 15)).orElseThrow();
        assertEquals(10, two.days());
    }

    @Test
    void 到職日是閏日時日期仍落在自己的週期內() {
        LocalDate hire = d(2024, 2, 29);
        for (LocalDate day = d(2024, 8, 29); day.isBefore(d(2030, 1, 1)); day = day.plusDays(1)) {
            var e = AnnualLeavePolicy.on(hire, day).orElseThrow();
            assertTrue(!day.isBefore(e.start()) && day.isBefore(e.endExclusive()), day.toString());
        }
    }
}
