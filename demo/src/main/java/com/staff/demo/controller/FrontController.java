package com.staff.demo.controller;

import com.staff.demo.service.AttendanceService;
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

import java.time.YearMonth;

/** 前台：員工打卡 */
@Controller
@RequestMapping("/front")
@RequiredArgsConstructor
public class FrontController {

    private final AttendanceService service;

    @GetMapping
    public String index(Authentication auth, Model model,
                        @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM") YearMonth month) {
        YearMonth ym = month == null ? YearMonth.now() : month;
        String username = auth.getName();
        model.addAttribute("employee", service.getEmployee(username));
        model.addAttribute("todayRecord", service.findToday(username).orElse(null));
        model.addAttribute("today", service.todayView(username));
        model.addAttribute("notice", service.todayNotice());
        model.addAttribute("records", service.monthViews(username, ym));
        model.addAttribute("month", ym.toString());
        model.addAttribute("prevMonth", ym.minusMonths(1).toString());
        model.addAttribute("nextMonth", ym.plusMonths(1).toString());
        model.addAttribute("hasNext", ym.isBefore(YearMonth.now()));
        return "front/index";
    }

    @PostMapping("/clock-in")
    public String clockIn(Authentication auth, RedirectAttributes ra) {
        ra.addFlashAttribute("message", service.clockIn(auth.getName()));
        return "redirect:/front";
    }

    @PostMapping("/clock-out")
    public String clockOut(Authentication auth, RedirectAttributes ra) {
        ra.addFlashAttribute("message", service.clockOut(auth.getName()));
        return "redirect:/front";
    }
}
