package com.staff.demo.controller;

import com.staff.demo.dto.MakeupPunchForm;
import com.staff.demo.entity.MakeupPunchRequest;
import com.staff.demo.service.MakeupPunchService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/** 補打卡：員工申請（/front/makeup）、主管審核（/front/makeup/approvals）、管理員註銷（/admin/makeup） */
@Controller
@RequiredArgsConstructor
public class MakeupPunchController {

    private final MakeupPunchService service;

    // ===================== 員工：申請與我的紀錄 =====================

    @GetMapping("/front/makeup")
    public String mine(Authentication auth, Model model) {
        model.addAttribute("form", new MakeupPunchForm());
        return mineView(auth, model);
    }

    @PostMapping("/front/makeup")
    public String apply(Authentication auth, @Valid @ModelAttribute("form") MakeupPunchForm form,
                        BindingResult br, Model model, RedirectAttributes ra) {
        if (!br.hasErrors()) {
            try {
                service.apply(auth.getName(), form);
                ra.addFlashAttribute("message", "補打卡申請已送出，等待審核。");
                return "redirect:/front/makeup";
            } catch (IllegalArgumentException ex) {
                br.reject("makeup", ex.getMessage());
            }
        }
        return mineView(auth, model);
    }

    @PostMapping("/front/makeup/{id}/cancel")
    public String cancel(@PathVariable Long id, Authentication auth, RedirectAttributes ra) {
        try {
            service.cancel(id, auth.getName());
            ra.addFlashAttribute("message", "已取消這筆補打卡申請。");
        } catch (IllegalArgumentException ex) {
            ra.addFlashAttribute("error", ex.getMessage());
        }
        return "redirect:/front/makeup";
    }

    private String mineView(Authentication auth, Model model) {
        model.addAttribute("requests", service.mine(auth.getName()));
        model.addAttribute("maxDays", MakeupPunchService.MAX_DAYS_BACK);
        return "front/makeup";
    }

    // ===================== 主管／管理員：審核 =====================

    @GetMapping("/front/makeup/approvals")
    public String approvals(Authentication auth, Model model) {
        model.addAttribute("requests", service.pendingFor(auth.getName()));
        return "front/makeup-approvals";
    }

    @PostMapping("/front/makeup/{id}/approve")
    public String approve(@PathVariable Long id, @RequestParam(required = false) String comment,
                          Authentication auth, RedirectAttributes ra) {
        return decide(id, true, comment, auth, ra);
    }

    @PostMapping("/front/makeup/{id}/reject")
    public String reject(@PathVariable Long id, @RequestParam(required = false) String comment,
                         Authentication auth, RedirectAttributes ra) {
        return decide(id, false, comment, auth, ra);
    }

    private String decide(Long id, boolean approve, String comment, Authentication auth, RedirectAttributes ra) {
        try {
            service.decide(id, auth.getName(), approve, comment);
            ra.addFlashAttribute("message", approve ? "已核准，打卡紀錄已更新。" : "已駁回。");
        } catch (IllegalArgumentException ex) {
            ra.addFlashAttribute("error", ex.getMessage());
        }
        return "redirect:/front/makeup/approvals";
    }

    // ===================== 後台：所有補打卡、註銷 =====================

    @GetMapping("/admin/makeup")
    public String adminList(@RequestParam(required = false) MakeupPunchRequest.Status status, Model model) {
        model.addAttribute("requests", service.all(status));
        model.addAttribute("status", status);
        return "admin/makeup";
    }

    @PostMapping("/admin/makeup/{id}/void")
    public String voidRequest(@PathVariable Long id, @RequestParam(required = false) String reason,
                              Authentication auth, RedirectAttributes ra) {
        try {
            service.voidApproved(id, auth.getName(), reason);
            ra.addFlashAttribute("message", "已註銷這筆補打卡，打卡紀錄已還原。");
        } catch (IllegalArgumentException ex) {
            ra.addFlashAttribute("error", ex.getMessage());
        }
        return "redirect:/admin/makeup";
    }
}
