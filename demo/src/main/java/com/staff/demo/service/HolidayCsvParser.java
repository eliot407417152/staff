package com.staff.demo.service;

import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.Charset;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.time.DateTimeException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 解析「中華民國政府行政機關辦公日曆表」CSV（純計算，不依賴 Spring）。
 * <ul>
 *   <li>人事行政總處版：西元日期(yyyyMMdd), 星期, 是否放假(0=上班, 2=放假), 備註</li>
 *   <li>新北市版：date, year, name, isholiday(是/否), holidaycategory, description</li>
 * </ul>
 * 欄位以標題列名稱判斷；日期接受 yyyyMMdd、yyyy/M/d、yyyy-MM-dd。
 */
public final class HolidayCsvParser {

    private HolidayCsvParser() {}

    public record Entry(LocalDate date, boolean holiday, String note) {}

    public static List<Entry> parse(byte[] bytes) {
        return parse(decode(bytes));
    }

    public static List<Entry> parse(String text) {
        if (text.startsWith("\uFEFF")) {
            text = text.substring(1);
        }
        String[] lines = text.split("\r?\n");
        int dateCol = 0, holidayCol = 2, noteCol = 3, nameCol = -1;
        int firstData = 0;

        // 標題列：第一個欄位不是日期就當標題
        if (lines.length > 0) {
            List<String> head = splitLine(lines[0]);
            if (!head.isEmpty() && parseDate(head.get(0)) == null) {
                String joined = String.join(",", head).toLowerCase(Locale.ROOT);
                if (joined.contains("subject") || joined.contains("start date")) {
                    throw new IllegalArgumentException("這是「Google 行事曆專用」格式，請改用一般版的 CSV（欄位：西元日期、星期、是否放假、備註）");
                }
                dateCol = holidayCol = noteCol = -1;
                for (int i = 0; i < head.size(); i++) {
                    String h = head.get(i).trim().toLowerCase(Locale.ROOT);
                    if (dateCol < 0 && (h.equals("date") || h.contains("日期"))) dateCol = i;
                    else if (holidayCol < 0 && (h.equals("isholiday") || h.contains("是否放假"))) holidayCol = i;
                    else if (noteCol < 0 && (h.equals("description") || h.contains("備註"))) noteCol = i;
                    else if (nameCol < 0 && (h.equals("name") || h.contains("節日"))) nameCol = i;
                }
                if (dateCol < 0 || holidayCol < 0) {
                    throw new IllegalArgumentException("看不懂這個 CSV 的欄位，需要有「日期」與「是否放假」欄位");
                }
                firstData = 1;
            }
        }

        Map<LocalDate, Entry> result = new LinkedHashMap<>();
        for (int i = firstData; i < lines.length; i++) {
            if (lines[i].isBlank()) {
                continue;
            }
            List<String> f = splitLine(lines[i]);
            LocalDate date = parseDate(get(f, dateCol));
            Boolean holiday = parseHoliday(get(f, holidayCol));
            if (date == null || holiday == null) {
                continue;   // 不是資料列就略過
            }
            String note = get(f, noteCol).trim();
            if (note.isEmpty() && nameCol >= 0) {
                note = get(f, nameCol).trim();
            }
            if (note.length() > 100) {
                note = note.substring(0, 100);
            }
            result.put(date, new Entry(date, holiday, note));
        }
        if (result.isEmpty()) {
            throw new IllegalArgumentException("CSV 裡沒有讀到任何有效的日期資料");
        }
        return new ArrayList<>(result.values());
    }

    /** 先試 UTF-8，不是的話改用 Big5（舊版檔案） */
    static String decode(byte[] bytes) {
        try {
            return StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(bytes)).toString();
        } catch (CharacterCodingException e) {
            return new String(bytes, Charset.forName("Big5"));
        }
    }

    static LocalDate parseDate(String raw) {
        if (raw == null) {
            return null;
        }
        String s = raw.trim().replace("\"", "");
        try {
            if (s.matches("\\d{8}")) {
                return LocalDate.of(Integer.parseInt(s.substring(0, 4)),
                        Integer.parseInt(s.substring(4, 6)), Integer.parseInt(s.substring(6, 8)));
            }
            String[] p = s.split("[/\\-.]");
            if (p.length == 3 && p[0].matches("\\d{4}") && p[1].matches("\\d{1,2}") && p[2].matches("\\d{1,2}")) {
                return LocalDate.of(Integer.parseInt(p[0]), Integer.parseInt(p[1]), Integer.parseInt(p[2]));
            }
        } catch (DateTimeException | NumberFormatException e) {
            return null;
        }
        return null;
    }

    /** 2、1、是、true 為放假；0、否、false 為上班；其他回傳 null */
    static Boolean parseHoliday(String raw) {
        String s = raw == null ? "" : raw.trim().replace("\"", "").toLowerCase(Locale.ROOT);
        return switch (s) {
            case "2", "1", "是", "true", "y", "yes" -> Boolean.TRUE;
            case "0", "否", "false", "n", "no" -> Boolean.FALSE;
            default -> null;
        };
    }

    private static String get(List<String> f, int i) {
        return i >= 0 && i < f.size() ? f.get(i) : "";
    }

    /** 會處理雙引號包住逗號的欄位 */
    static List<String> splitLine(String line) {
        List<String> out = new ArrayList<>();
        StringBuilder cur = new StringBuilder();
        boolean quoted = false;
        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (c == '"') {
                if (quoted && i + 1 < line.length() && line.charAt(i + 1) == '"') {
                    cur.append('"');
                    i++;
                } else {
                    quoted = !quoted;
                }
            } else if (c == ',' && !quoted) {
                out.add(cur.toString());
                cur.setLength(0);
            } else {
                cur.append(c);
            }
        }
        out.add(cur.toString());
        return out;
    }
}
