package com.staff.demo.controller;

import com.staff.demo.service.AttendanceService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/** 前台：員工打卡 */
@Controller
@RequestMapping("/front")
@RequiredArgsConstructor
public class FrontController {

    private final AttendanceService service;

    @GetMapping
    public String index(Authentication auth, Model model) {
        String username = auth.getName();
        model.addAttribute("employee", service.getEmployee(username));
        model.addAttribute("todayRecord", service.findToday(username).orElse(null));
        model.addAttribute("today", service.todayView(username));
        model.addAttribute("notice", service.todayNotice());
        model.addAttribute("records", service.recentViews(username));
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
