package com.staff.demo.controller;

import com.staff.demo.service.AttendanceService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

/** 後台：管理員查看今日出勤 */
@Controller
@RequestMapping("/admin")
@RequiredArgsConstructor
public class AdminController {

    private final AttendanceService service;

    @GetMapping
    public String index(Authentication auth, Model model) {
        model.addAttribute("admin", service.getEmployee(auth.getName()));
        model.addAttribute("records", service.todayAll());
        return "admin/index";
    }
}
