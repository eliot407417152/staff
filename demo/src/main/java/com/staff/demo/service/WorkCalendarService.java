package com.staff.demo.service;

import com.staff.demo.entity.CalendarDay;
import com.staff.demo.entity.TyphoonDay;
import com.staff.demo.repository.CalendarDayRepository;
import com.staff.demo.repository.TyphoonDayRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.DayOfWeek;
import java.time.Duration;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 辦公日曆：國定假日（來自政府資料開放平臺「中華民國政府行政機關辦公日曆表」）
 * 與颱風假（管理員手動登記）。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class WorkCalendarService {

    private final CalendarDayRepository dayRepo;
    private final TyphoonDayRepository typhoonRepo;

    /** 逗號分隔的 CSV 下載網址，見 application.properties */
    @Value("${app.calendar.source-urls:}")
    private String sourceUrls;

    @Value("${app.calendar.auto-sync:true}")
    private boolean autoSync;

    public record SyncResult(int sources, int rows) {}

    /** 一段日期範圍內的上班日判斷（先把資料一次查好，避免每天查一次資料庫） */
    public static final class Days {
        private final Map<LocalDate, CalendarDay> calendar;
        private final Set<LocalDate> typhoon;

        Days(Map<LocalDate, CalendarDay> calendar, Set<LocalDate> typhoon) {
            this.calendar = calendar;
            this.typhoon = typhoon;
        }

        /** 依辦公日曆表是否為上班日；沒有該日資料時，以週一至週五為上班日 */
        public boolean isOfficialWorkday(LocalDate d) {
            CalendarDay c = calendar.get(d);
            if (c != null) {
                return !c.isHoliday();
            }
            DayOfWeek w = d.getDayOfWeek();
            return w != DayOfWeek.SATURDAY && w != DayOfWeek.SUNDAY;
        }

        public boolean isTyphoon(LocalDate d) {
            return typhoon.contains(d);
        }

        /** 真正要上班的日子：辦公日曆為上班日，且沒有颱風假 */
        public boolean isWorkday(LocalDate d) {
            return isOfficialWorkday(d) && !isTyphoon(d);
        }

        /** 這天不上班的原因；上班日回傳 null */
        public String offReason(LocalDate d) {
            if (isTyphoon(d)) {
                return "颱風假";
            }
            if (isOfficialWorkday(d)) {
                return null;
            }
            CalendarDay c = calendar.get(d);
            if (c != null && c.getNote() != null && !c.getNote().isBlank()) {
                return c.getNote();
            }
            DayOfWeek w = d.getDayOfWeek();
            return w == DayOfWeek.SATURDAY || w == DayOfWeek.SUNDAY ? "週末" : "國定假日";
        }
    }

    // ===================== 查詢 =====================

    public Days days(LocalDate from, LocalDate to) {
        Map<LocalDate, CalendarDay> cal = new HashMap<>();
        for (CalendarDay c : dayRepo.findByDateBetweenOrderByDateAsc(from, to)) {
            cal.put(c.getDate(), c);
        }
        Set<LocalDate> ty = new HashSet<>();
        for (TyphoonDay t : typhoonRepo.findByDateBetweenOrderByDateDesc(from, to)) {
            ty.add(t.getDate());
        }
        return new Days(cal, ty);
    }

    public boolean isTyphoonDay(LocalDate date) {
        return typhoonRepo.existsById(date);
    }

    public List<TyphoonDay> typhoonDays() {
        return typhoonRepo.findAllByOrderByDateDesc();
    }

    /** 該年度已匯入的辦公日曆筆數（0 代表還沒匯入） */
    public long rowCount(int year) {
        return dayRepo.countByDateBetween(LocalDate.of(year, 1, 1), LocalDate.of(year, 12, 31));
    }

    /** 該年度的特殊日子：平日放假（國定假日、補假）與假日補班 */
    public List<CalendarDay> specialDays(int year) {
        List<CalendarDay> out = new ArrayList<>();
        for (CalendarDay c : dayRepo.findByDateBetweenOrderByDateAsc(LocalDate.of(year, 1, 1), LocalDate.of(year, 12, 31))) {
            DayOfWeek w = c.getDate().getDayOfWeek();
            boolean weekend = w == DayOfWeek.SATURDAY || w == DayOfWeek.SUNDAY;
            if (c.isHoliday() != weekend) {
                out.add(c);
            }
        }
        return out;
    }

    // ===================== 匯入辦公日曆表 =====================

    /** 從設定的網址下載並匯入 */
    public SyncResult sync() {
        List<String> urls = Arrays.stream(sourceUrls.split(","))
                .map(String::trim).filter(s -> !s.isEmpty()).toList();
        if (urls.isEmpty()) {
            throw new IllegalStateException("尚未設定下載網址（application.properties 的 app.calendar.source-urls），請改用上傳 CSV");
        }
        int rows = 0;
        int ok = 0;
        List<String> errors = new ArrayList<>();
        for (String url : urls) {
            try {
                rows += importCsv(download(url));
                ok++;
            } catch (IOException | InterruptedException | RuntimeException e) {
                if (e instanceof InterruptedException) {
                    Thread.currentThread().interrupt();
                }
                log.warn("辦公日曆下載失敗：{}", url, e);
                errors.add(url + "（" + e.getMessage() + "）");
            }
        }
        if (ok == 0) {
            throw new IllegalStateException("下載失敗：" + String.join("；", errors));
        }
        return new SyncResult(ok, rows);
    }

    /** 匯入 CSV 內容，已存在的日期會被更新；回傳匯入筆數 */
    public int importCsv(byte[] csv) {
        List<HolidayCsvParser.Entry> entries = HolidayCsvParser.parse(csv);
        List<CalendarDay> days = entries.stream()
                .map(e -> new CalendarDay(e.date(), e.holiday(), e.note())).toList();
        dayRepo.saveAll(days);
        return days.size();
    }

    private byte[] download(String url) throws IOException, InterruptedException {
        HttpClient client = HttpClient.newBuilder()
                .followRedirects(HttpClient.Redirect.NORMAL)
                .connectTimeout(Duration.ofSeconds(5)).build();
        HttpRequest req = HttpRequest.newBuilder(URI.create(url))
                .timeout(Duration.ofSeconds(20)).GET().build();
        HttpResponse<byte[]> res = client.send(req, HttpResponse.BodyHandlers.ofByteArray());
        if (res.statusCode() != 200) {
            throw new IOException("HTTP " + res.statusCode());
        }
        return res.body();
    }

    /** 啟動時如果今年還沒有日曆資料，就試著自動下載一次（失敗只記 log，不影響啟動） */
    @EventListener(ApplicationReadyEvent.class)
    public void syncOnStartup() {
        if (autoSync && rowCount(LocalDate.now().getYear()) == 0) {
            trySync();
        }
    }

    /** 每週一凌晨 3 點自動同步（人事總處每年 7、8 月公布隔年日曆，也偶有修正） */
    @Scheduled(cron = "${app.calendar.sync-cron:0 0 3 * * MON}")
    public void scheduledSync() {
        if (autoSync) {
            trySync();
        }
    }

    private void trySync() {
        try {
            SyncResult r = sync();
            log.info("辦公日曆自動同步完成：{} 筆", r.rows());
        } catch (RuntimeException e) {
            log.warn("辦公日曆自動同步失敗：{}", e.getMessage());
        }
    }

    // ===================== 颱風假 =====================

    @Transactional
    public void addTyphoon(LocalDate date, String note, String adminUsername) {
        if (typhoonRepo.existsById(date)) {
            throw new IllegalArgumentException("這一天已經登記為颱風假");
        }
        if (!days(date, date).isOfficialWorkday(date)) {
            throw new IllegalArgumentException("這一天本來就是放假日，不需要登記颱風假");
        }
        TyphoonDay t = new TyphoonDay();
        t.setDate(date);
        t.setNote(note == null || note.isBlank() ? null : note.trim());
        t.setCreatedBy(adminUsername);
        typhoonRepo.save(t);
    }

    @Transactional
    public void removeTyphoon(LocalDate date) {
        if (!typhoonRepo.existsById(date)) {
            throw new IllegalArgumentException("找不到這一天的颱風假登記");
        }
        typhoonRepo.deleteById(date);
    }
}
