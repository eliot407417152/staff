package com.staff.demo.controller;

import com.staff.demo.dto.ForgotPasswordForm;
import com.staff.demo.service.AccountService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.mail.MailException;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;

@Controller
@RequiredArgsConstructor
@Slf4j
public class LoginController {

    private final AccountService accountService;

    @GetMapping("/login")
    public String login() {
        return "login";
    }

    /** 登入後的分流：管理員去後台，其他人去前台 */
    @GetMapping("/")
    public String home(Authentication auth) {
        boolean isAdmin = auth.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"));
        return isAdmin ? "redirect:/admin" : "redirect:/front";
    }

    @GetMapping("/forgot-password")
    public String forgotForm(Model model) {
        model.addAttribute("form", new ForgotPasswordForm());
        return "forgot-password";
    }

    /** 不論帳號是否存在，成功時都顯示相同訊息 */
    @PostMapping("/forgot-password")
    public String forgot(@Valid @ModelAttribute("form") ForgotPasswordForm form,
                         BindingResult br, Model model) {
        if (br.hasErrors()) {
            return "forgot-password";
        }
        try {
            accountService.resetPassword(form.getUsername(), form.getEmail());
            model.addAttribute("done", true);
        } catch (MailException ex) {
            log.error("寄送新密碼失敗", ex);
            br.reject("mail", "目前無法寄送郵件，請稍後再試或聯絡管理員。");
        }
        return "forgot-password";
    }
}
