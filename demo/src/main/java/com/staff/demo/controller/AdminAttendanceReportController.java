package com.staff.demo.controller;

import com.staff.demo.repository.DepartmentRepository;
import com.staff.demo.service.AttendanceReportService;
import com.staff.demo.service.TyphoonService;
import com.staff.demo.service.WorkSchedule;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;

/** 後台：異常出勤（工時不足、未打卡、颱風假扣薪），可依部門、分類、處理狀態篩選，並由管理員註銷 */
@Controller
@RequestMapping("/admin/abnormal")
@RequiredArgsConstructor
public class AdminAttendanceReportController {

    /** 分類代碼：颱風假不是出勤異常的一種，另外處理 */
    private static final String TYPHOON = "TYPHOON";

    private final AttendanceReportService reportService;
    private final TyphoonService typhoonService;
    private final DepartmentRepository departmentRepo;

    @GetMapping
    public String page(@RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM") YearMonth month,
                       @RequestParam(required = false) Long dept,
                       @RequestParam(required = false) String category,
                       @RequestParam(required = false) String state,
                       Model model) {
        YearMonth ym = month == null ? YearMonth.now() : month;
        String cat = category == null ? "" : category.trim();
        boolean typhoonOnly = TYPHOON.equals(cat);
        WorkSchedule.Status abnormalCategory = parseCategory(cat);
        if (!cat.isEmpty() && !typhoonOnly && abnormalCategory == null) {
            cat = "";   // 不認得的分類，當作「全部」
        }
        AttendanceReportService.State st = parseState(state);

        boolean showTyphoon = cat.isEmpty() || typhoonOnly;
        boolean showAbnormal = !typhoonOnly;

        List<TyphoonService.Line> lines = List.of();
        if (showTyphoon) {
            lines = typhoonService.lines(ym.atDay(1), ym.atEndOfMonth()).stream()
                    .filter(l -> dept == null || dept.equals(l.getDepartmentId())).toList();
        }
        model.addAttribute("typhoonLines", lines);
        model.addAttribute("typhoonSummaries", typhoonService.summaries(lines));
        model.addAttribute("abnormal", showAbnormal
                ? reportService.abnormal(ym, dept, abnormalCategory, st) : List.of());

        model.addAttribute("showTyphoon", showTyphoon);
        model.addAttribute("showAbnormal", showAbnormal);
        model.addAttribute("departments", departmentRepo.findByActiveTrueOrderByCodeAsc());
        model.addAttribute("month", ym.toString());
        model.addAttribute("prevMonth", ym.minusMonths(1).toString());
        model.addAttribute("nextMonth", ym.plusMonths(1).toString());
        model.addAttribute("deptParam", dept == null ? "" : dept.toString());
        model.addAttribute("categoryParam", cat);
        model.addAttribute("stateParam", st.name());
        model.addAttribute("states", AttendanceReportService.State.values());
        return "admin/abnormal";
    }

    /** 註銷：確認這筆異常不成立（例如其實有來上班） */
    @PostMapping("/resolve")
    public String resolve(@RequestParam Long employeeId,
                          @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
                          @RequestParam(required = false) String reason,
                          @RequestParam(required = false) String month,
                          @RequestParam(required = false) String dept,
                          @RequestParam(required = false) String category,
                          @RequestParam(required = false) String state,
                          Authentication auth, RedirectAttributes ra) {
        try {
            reportService.resolve(auth.getName(), employeeId, date, reason);
            ra.addFlashAttribute("message", "已註銷 " + date + " 的異常，可在「已註銷」查看或撤銷。");
        } catch (IllegalArgumentException ex) {
            ra.addFlashAttribute("error", ex.getMessage());
        }
        return back(ra, month, dept, category, state);
    }

    /** 撤銷註銷：異常重新回到待處理 */
    @PostMapping("/revoke")
    public String revoke(@RequestParam Long employeeId,
                         @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
                         @RequestParam(required = false) String month,
                         @RequestParam(required = false) String dept,
                         @RequestParam(required = false) String category,
                         @RequestParam(required = false) String state,
                         Authentication auth, RedirectAttributes ra) {
        try {
            reportService.revoke(auth.getName(), employeeId, date);
            ra.addFlashAttribute("message", "已撤銷註銷，" + date + " 的異常回到待處理。");
        } catch (IllegalArgumentException ex) {
            ra.addFlashAttribute("error", ex.getMessage());
        }
        return back(ra, month, dept, category, state);
    }

    private static String back(RedirectAttributes ra, String month, String dept, String category, String state) {
        addIfPresent(ra, "month", month);
        addIfPresent(ra, "dept", dept);
        addIfPresent(ra, "category", category);
        addIfPresent(ra, "state", state);
        return "redirect:/admin/abnormal";
    }

    private static void addIfPresent(RedirectAttributes ra, String name, String value) {
        if (value != null && !value.isBlank()) {
            ra.addAttribute(name, value);
        }
    }

    private static WorkSchedule.Status parseCategory(String code) {
        try {
            WorkSchedule.Status s = WorkSchedule.Status.valueOf(code);
            return s.isAbnormal() ? s : null;
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    private static AttendanceReportService.State parseState(String code) {
        try {
            return AttendanceReportService.State.valueOf(code);
        } catch (IllegalArgumentException | NullPointerException ex) {
            return AttendanceReportService.State.PENDING;
        }
    }
}
