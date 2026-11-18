package com.staff.demo.controller;

import com.staff.demo.service.AttendanceReportService;
import com.staff.demo.service.TyphoonService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.time.YearMonth;
import java.util.List;

/** 後台：異常出勤（工時不足、未打卡、颱風假扣薪） */
@Controller
@RequestMapping("/admin/abnormal")
@RequiredArgsConstructor
public class AdminAttendanceReportController {

    private final AttendanceReportService reportService;
    private final TyphoonService typhoonService;

    @GetMapping
    public String page(@RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM") YearMonth month,
                       Model model) {
        YearMonth ym = month == null ? YearMonth.now() : month;
        List<TyphoonService.Line> lines = typhoonService.lines(ym.atDay(1), ym.atEndOfMonth());
        model.addAttribute("month", ym.toString());
        model.addAttribute("prevMonth", ym.minusMonths(1).toString());
        model.addAttribute("nextMonth", ym.plusMonths(1).toString());
        model.addAttribute("typhoonLines", lines);
        model.addAttribute("typhoonSummaries", typhoonService.summaries(lines));
        model.addAttribute("abnormal", reportService.abnormal(ym));
        return "admin/abnormal";
    }
}
