package com.staff.demo.controller;

import com.staff.demo.service.WorkCalendarService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.io.IOException;
import java.time.LocalDate;

/** 後台：辦公日曆（國定假日）與颱風假管理 */
@Controller
@RequestMapping("/admin/calendar")
@RequiredArgsConstructor
public class AdminCalendarController {

    private final WorkCalendarService calendar;

    @GetMapping
    public String page(@RequestParam(required = false) Integer year, Model model) {
        int y = year == null ? LocalDate.now().getYear() : year;
        model.addAttribute("year", y);
        model.addAttribute("rowCount", calendar.rowCount(y));
        model.addAttribute("specialDays", calendar.specialDays(y));
        model.addAttribute("typhoons", calendar.typhoonDays());
        return "admin/calendar";
    }

    /** 從設定的網址（政府資料開放平臺）下載並匯入 */
    @PostMapping("/sync")
    public String sync(RedirectAttributes ra) {
        try {
            WorkCalendarService.SyncResult r = calendar.sync();
            ra.addFlashAttribute("message", "同步完成，共更新 " + r.rows() + " 筆日期資料。");
        } catch (RuntimeException ex) {
            ra.addFlashAttribute("error", ex.getMessage());
        }
        return "redirect:/admin/calendar";
    }

    /** 自行從 data.gov.tw 下載 CSV 後上傳 */
    @PostMapping("/import")
    public String importCsv(@RequestParam("file") MultipartFile file, RedirectAttributes ra) {
        if (file.isEmpty()) {
            ra.addFlashAttribute("error", "請選擇要上傳的 CSV 檔案。");
            return "redirect:/admin/calendar";
        }
        try {
            int rows = calendar.importCsv(file.getBytes());
            ra.addFlashAttribute("message", "匯入完成，共更新 " + rows + " 筆日期資料。");
        } catch (IOException ex) {
            ra.addFlashAttribute("error", "讀取檔案失敗：" + ex.getMessage());
        } catch (IllegalArgumentException ex) {
            ra.addFlashAttribute("error", ex.getMessage());
        }
        return "redirect:/admin/calendar";
    }

    @PostMapping("/typhoon")
    public String addTyphoon(@RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
                             @RequestParam(required = false) String note,
                             Authentication auth, RedirectAttributes ra) {
        try {
            calendar.addTyphoon(date, note, auth.getName());
            ra.addFlashAttribute("message", "已登記 " + date + " 為颱風假。員工可到「請假」頁選擇用特休補上，未補上的視為天災假並扣薪。");
        } catch (IllegalArgumentException ex) {
            ra.addFlashAttribute("error", ex.getMessage());
        }
        return "redirect:/admin/calendar";
    }

    @PostMapping("/typhoon/{date}/delete")
    public String removeTyphoon(@PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
                                RedirectAttributes ra) {
        try {
            calendar.removeTyphoon(date);
            ra.addFlashAttribute("message", "已取消 " + date + " 的颱風假登記。");
        } catch (IllegalArgumentException ex) {
            ra.addFlashAttribute("error", ex.getMessage());
        }
        return "redirect:/admin/calendar";
    }
}
