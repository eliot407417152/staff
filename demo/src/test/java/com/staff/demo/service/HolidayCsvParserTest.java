package com.staff.demo.service;

import org.junit.jupiter.api.Test;

import java.nio.charset.Charset;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class HolidayCsvParserTest {

    @Test
    void 人事總處格式() {
        String csv = "\uFEFF西元日期,星期,是否放假,備註\r\n20260101,四,2,開國紀念日\r\n20260102,五,0,\r\n20260103,六,2,\r\n";
        List<HolidayCsvParser.Entry> es = HolidayCsvParser.parse(csv);
        assertEquals(3, es.size());
        assertEquals(LocalDate.of(2026, 1, 1), es.get(0).date());
        assertEquals(true, es.get(0).holiday());
        assertEquals("開國紀念日", es.get(0).note());
        assertEquals(false, es.get(1).holiday());
    }

    @Test
    void 新北市格式_引號內有逗號() {
        String csv = "date,year,name,isholiday,holidaycategory,description\n"
                + "2026/2/16,2026,春節,是,放假之紀念日及節日,\"農曆除夕, 補假\"\n"
                + "2026/2/9,2026,,否,,補行上班\n";
        List<HolidayCsvParser.Entry> es = HolidayCsvParser.parse(csv);
        assertEquals(LocalDate.of(2026, 2, 16), es.get(0).date());
        assertEquals(true, es.get(0).holiday());
        assertEquals("農曆除夕, 補假", es.get(0).note());
        assertEquals(false, es.get(1).holiday());
        assertEquals("補行上班", es.get(1).note());
    }

    @Test
    void 沒有標題列與Big5編碼() {
        assertEquals(2, HolidayCsvParser.parse("20260101,四,2,元旦\n20260102,五,0,\n").size());
        byte[] big5 = "西元日期,星期,是否放假,備註\n20260101,四,2,元旦\n".getBytes(Charset.forName("Big5"));
        assertEquals(1, HolidayCsvParser.parse(big5).size());
    }

    @Test
    void 看不懂的格式要拒絕() {
        assertThrows(IllegalArgumentException.class,
                () -> HolidayCsvParser.parse("Subject,Start Date,All Day Event\nx,1/1/2026,TRUE\n"));
        assertThrows(IllegalArgumentException.class, () -> HolidayCsvParser.parse("foo\nbar\n"));
    }
}
