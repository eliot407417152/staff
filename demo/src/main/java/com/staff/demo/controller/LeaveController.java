package com.staff.demo.controller;

import com.staff.demo.dto.LeaveRequestForm;
import com.staff.demo.entity.LeaveRequest;
import com.staff.demo.service.LeaveService;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.List;

/** 請假：申請、取消、審核。網頁（/front/leaves、/front/approvals、/admin/leaves）與 API（/api/leaves） */
@Controller
@RequiredArgsConstructor
public class LeaveController {

    private final LeaveService leaveService;

    // ===================== 網頁：我的請假 =====================

    @GetMapping("/front/leaves")
    public String myLeaves(Authentication auth, Model model) {
        model.addAttribute("form", new LeaveRequestForm());
        return leavesView(auth, model);
    }

    @PostMapping("/front/leaves")
    public String apply(Authentication auth, @Valid @ModelAttribute("form") LeaveRequestForm form,
                        BindingResult br, Model model, RedirectAttributes ra) {
        if (!br.hasErrors()) {
            try {
                leaveService.apply(auth.getName(), form);
                ra.addFlashAttribute("message", "請假申請已送出，等待審核。");
                return "redirect:/front/leaves";
            } catch (IllegalArgumentException ex) {
                br.reject("leave", ex.getMessage());
            }
        }
        return leavesView(auth, model);
    }

    @PostMapping("/front/leaves/{id}/cancel")
    public String cancel(@PathVariable Long id, Authentication auth, RedirectAttributes ra) {
        try {
            leaveService.cancel(id, auth.getName());
            ra.addFlashAttribute("message", "已取消這筆請假申請。");
        } catch (IllegalArgumentException ex) {
            ra.addFlashAttribute("error", ex.getMessage());
        }
        return "redirect:/front/leaves";
    }

    private String leavesView(Authentication auth, Model model) {
        model.addAttribute("types", leaveService.activeTypes());
        model.addAttribute("balances", leaveService.balances(auth.getName()));
        model.addAttribute("leaves", leaveService.mine(auth.getName()));
        return "front/leaves";
    }

    // ===================== 網頁：審核 =====================

    @GetMapping("/front/approvals")
    public String approvals(Authentication auth, Model model) {
        model.addAttribute("requests", leaveService.pendingFor(auth.getName()));
        return "front/approvals";
    }

    @PostMapping("/front/approvals/{id}/approve")
    public String approve(@PathVariable Long id, @RequestParam(required = false) String comment,
                          Authentication auth, RedirectAttributes ra) {
        return decide(id, true, comment, auth, ra);
    }

    @PostMapping("/front/approvals/{id}/reject")
    public String reject(@PathVariable Long id, @RequestParam(required = false) String comment,
                         Authentication auth, RedirectAttributes ra) {
        return decide(id, false, comment, auth, ra);
    }

    private String decide(Long id, boolean approve, String comment, Authentication auth, RedirectAttributes ra) {
        try {
            leaveService.decide(id, auth.getName(), approve, comment);
            ra.addFlashAttribute("message", approve ? "已核准。" : "已駁回。");
        } catch (IllegalArgumentException ex) {
            ra.addFlashAttribute("error", ex.getMessage());
        }
        return "redirect:/front/approvals";
    }

    // ===================== 網頁：後台請假紀錄 =====================

    @GetMapping("/admin/leaves")
    public String adminLeaves(@RequestParam(required = false) LeaveRequest.Status status, Model model) {
        model.addAttribute("leaves", leaveService.all(status));
        model.addAttribute("status", status);
        return "admin/leaves";
    }

    // ===================== REST API（Swagger 會顯示） =====================

    private static final String TAG = "請假";

    @Operation(summary = "可申請的假別與年度額度", tags = TAG)
    @GetMapping("/api/leaves/types")
    @ResponseBody
    public List<LeaveService.TypeView> apiTypes() {
        return leaveService.typeViews();
    }

    @Operation(summary = "我今年各假別的額度與使用狀況（小時）", tags = TAG)
    @GetMapping("/api/leaves/balances")
    @ResponseBody
    public List<LeaveService.BalanceView> apiBalances(Authentication auth) {
        return leaveService.balances(auth.getName());
    }

    @Operation(summary = "我的請假紀錄", tags = TAG)
    @GetMapping("/api/leaves")
    @ResponseBody
    public List<LeaveService.LeaveView> apiMine(Authentication auth) {
        return leaveService.mine(auth.getName()).stream().map(leaveService::toView).toList();
    }

    @Operation(summary = "申請請假", tags = TAG,
            description = "時間格式如 2026-11-12T09:00:00。時數只計上班時間（09:00–12:00、13:00–18:00，週末不計）。")
    @PostMapping("/api/leaves")
    @ResponseBody
    @ResponseStatus(HttpStatus.CREATED)
    public LeaveService.LeaveView apiApply(Authentication auth, @Valid @RequestBody LeaveRequestForm form) {
        return leaveService.toView(leaveService.apply(auth.getName(), form));
    }

    @Operation(summary = "取消我的請假", tags = TAG, description = "審核中，或已核准但尚未開始的請假可以取消。")
    @PostMapping("/api/leaves/{id}/cancel")
    @ResponseBody
    public LeaveService.LeaveView apiCancel(@PathVariable Long id, Authentication auth) {
        return leaveService.toView(leaveService.cancel(id, auth.getName()));
    }

    @Operation(summary = "待我審核的請假", tags = TAG)
    @GetMapping("/api/leaves/approvals")
    @ResponseBody
    public List<LeaveService.LeaveView> apiApprovals(Authentication auth) {
        return leaveService.pendingFor(auth.getName()).stream().map(leaveService::toView).toList();
    }

    @Operation(summary = "核准請假", tags = TAG, description = "只有該員工的直屬主管或管理員可以審核，不能審核自己的申請。")
    @PostMapping("/api/leaves/{id}/approve")
    @ResponseBody
    public LeaveService.LeaveView apiApprove(@PathVariable Long id, @RequestParam(required = false) String comment,
                                             Authentication auth) {
        return leaveService.toView(leaveService.decide(id, auth.getName(), true, comment));
    }

    @Operation(summary = "駁回請假", tags = TAG, description = "駁回時 comment（原因）必填。")
    @PostMapping("/api/leaves/{id}/reject")
    @ResponseBody
    public LeaveService.LeaveView apiReject(@PathVariable Long id, @RequestParam(required = false) String comment,
                                            Authentication auth) {
        return leaveService.toView(leaveService.decide(id, auth.getName(), false, comment));
    }

    @Operation(summary = "後台：所有請假紀錄", tags = "後台－請假",
            description = "可用 status 篩選：PENDING、APPROVED、REJECTED、CANCELLED。")
    @GetMapping("/api/admin/leaves")
    @ResponseBody
    public List<LeaveService.LeaveView> apiAll(@RequestParam(required = false) LeaveRequest.Status status) {
        return leaveService.all(status).stream().map(leaveService::toView).toList();
    }
}
