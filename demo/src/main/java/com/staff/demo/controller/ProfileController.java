package com.staff.demo.controller;

import com.staff.demo.dto.PasswordChangeForm;
import com.staff.demo.dto.ProfileForm;
import com.staff.demo.entity.Employee;
import com.staff.demo.service.AccountService;
import com.staff.demo.service.EmployeeService;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.Map;

/** 員工個人資料與變更密碼：網頁（/front/profile）與 API（/api/profile） */
@Controller
@RequiredArgsConstructor
public class ProfileController {

    private final EmployeeService employeeService;
    private final AccountService accountService;

    // ===================== 網頁 =====================

    @GetMapping("/front/profile")
    public String view(Authentication auth, Model model) {
        Employee employee = employeeService.getByUsername(auth.getName());
        model.addAttribute("form", ProfileForm.of(employee));
        model.addAttribute("pwForm", new PasswordChangeForm());
        return profileView(model, employee);
    }

    @PostMapping("/front/profile")
    public String save(Authentication auth,
                       @Valid @ModelAttribute("form") ProfileForm form,
                       BindingResult br, Model model, RedirectAttributes ra) {
        if (br.hasErrors()) {
            model.addAttribute("pwForm", new PasswordChangeForm());
            return profileView(model, employeeService.getByUsername(auth.getName()));
        }
        employeeService.updateProfile(auth.getName(), form);
        ra.addFlashAttribute("message", "個人資料已更新。");
        return "redirect:/front/profile";
    }

    @PostMapping("/front/profile/password")
    public String changePassword(Authentication auth,
                                 @Valid @ModelAttribute("pwForm") PasswordChangeForm pwForm,
                                 BindingResult br, Model model, RedirectAttributes ra) {
        if (!br.hasErrors()) {
            try {
                accountService.changePassword(auth.getName(), pwForm);
                ra.addFlashAttribute("message", "密碼已變更。");
                return "redirect:/front/profile";
            } catch (IllegalArgumentException ex) {
                br.reject("password", ex.getMessage());
            }
        }
        Employee employee = employeeService.getByUsername(auth.getName());
        model.addAttribute("form", ProfileForm.of(employee));
        return profileView(model, employee);
    }

    private String profileView(Model model, Employee employee) {
        model.addAttribute("employee", employee);
        model.addAttribute("mustChange", employee.isMustChangePassword());
        return "front/profile";
    }

    // ===================== REST API（Swagger 會顯示） =====================

    @Operation(summary = "查詢我的個人資料", tags = "個人資料")
    @GetMapping("/api/profile")
    @ResponseBody
    public EmployeeService.EmployeeView apiMe(Authentication auth) {
        return employeeService.toView(employeeService.getByUsername(auth.getName()));
    }

    @Operation(summary = "修改我的個人資料", tags = "個人資料",
            description = "只能改姓名、Email、電話、生日、地址、緊急聯絡人。員工編號、部門、職稱等由後台維護。")
    @PutMapping("/api/profile")
    @ResponseBody
    public EmployeeService.EmployeeView apiUpdate(Authentication auth, @Valid @RequestBody ProfileForm form) {
        employeeService.updateProfile(auth.getName(), form);
        return employeeService.toView(employeeService.getByUsername(auth.getName()));
    }

    @Operation(summary = "變更我的密碼", tags = "個人資料")
    @PutMapping("/api/profile/password")
    @ResponseBody
    public Map<String, String> apiChangePassword(Authentication auth, @Valid @RequestBody PasswordChangeForm form) {
        accountService.changePassword(auth.getName(), form);
        return Map.of("message", "密碼已變更");
    }
}
