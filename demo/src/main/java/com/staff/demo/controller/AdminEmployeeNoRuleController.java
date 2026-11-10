package com.staff.demo.controller;

import com.staff.demo.dto.EmployeeNoRuleForm;
import com.staff.demo.entity.EmployeeNoRule;
import com.staff.demo.service.EmployeeNoService;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.time.LocalDate;

/** 後台員工編號規則：網頁（/admin/settings/employee-no）與 API（/api/admin/settings/employee-no） */
@Controller
@RequiredArgsConstructor
public class AdminEmployeeNoRuleController {

    private final EmployeeNoService service;

    // ===================== 網頁 =====================

    @GetMapping("/admin/settings/employee-no")
    public String view(Model model) {
        EmployeeNoRule rule = service.getRule();
        model.addAttribute("form", EmployeeNoRuleForm.of(rule));
        model.addAttribute("preview", service.preview(rule, LocalDate.now(), null));
        return "admin/employee-no-rule";
    }

    @PostMapping("/admin/settings/employee-no")
    public String save(@Valid @ModelAttribute("form") EmployeeNoRuleForm form,
                       BindingResult br, Model model, RedirectAttributes ra) {
        if (br.hasErrors()) {
            model.addAttribute("preview", "-");
            return "admin/employee-no-rule";
        }
        service.updateRule(form);
        ra.addFlashAttribute("message", "員工編號規則已更新，之後新增的員工會套用新規則。");
        return "redirect:/admin/settings/employee-no";
    }

    // ===================== REST API（Swagger 會顯示） =====================

    private static final String TAG = "後台－員工編號規則";

    public record RuleResponse(EmployeeNoRuleForm rule, String nextNumberPreview) {}

    @Operation(summary = "查詢目前的員工編號規則與下一個編號範例", tags = TAG)
    @GetMapping("/api/admin/settings/employee-no")
    @ResponseBody
    public RuleResponse apiGet() {
        return ruleResponse();
    }

    @Operation(summary = "修改員工編號規則", tags = TAG,
            description = "只影響之後新增的員工，不會變動既有員工編號。")
    @PutMapping("/api/admin/settings/employee-no")
    @ResponseBody
    public RuleResponse apiUpdate(@Valid @RequestBody EmployeeNoRuleForm form) {
        service.updateRule(form);
        return ruleResponse();
    }

    private RuleResponse ruleResponse() {
        EmployeeNoRule rule = service.getRule();
        return new RuleResponse(EmployeeNoRuleForm.of(rule), service.preview(rule, LocalDate.now(), null));
    }
}
