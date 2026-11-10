package com.staff.demo.controller;

import com.staff.demo.entity.Employee;
import com.staff.demo.repository.EmployeeRepository;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ModelAttribute;

import java.util.List;
import java.util.Map;

/** 全域設定：每個頁面的導覽列資料，以及 API 的錯誤訊息格式 */
@ControllerAdvice
@RequiredArgsConstructor
public class GlobalModelAdvice {

    private final EmployeeRepository employeeRepo;

    @Getter
    @AllArgsConstructor
    public static class NavInfo {
        private final String name;
        private final boolean admin;
        /** 是否顯示「請假審核」：管理員，或有直屬下屬的主管 */
        private final boolean canApprove;
        /** 尚未登記 Email：顯示提醒橫幅 */
        private final boolean emailMissing;
    }

    /** 讓每個頁面都能用 ${nav.name}、${nav.admin}、${nav.canApprove} 畫出導覽列 */
    @ModelAttribute("nav")
    public NavInfo nav(Authentication auth) {
        if (auth == null || auth instanceof AnonymousAuthenticationToken) {
            return null;
        }
        Employee me = employeeRepo.findByUsername(auth.getName()).orElse(null);
        boolean admin = auth.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"));
        boolean canApprove = admin || (me != null && employeeRepo.existsByManager(me));
        boolean emailMissing = me != null && (me.getEmail() == null || me.getEmail().isBlank());
        return new NavInfo(me == null ? auth.getName() : me.getName(), admin, canApprove, emailMissing);
    }

    /** API：@Valid @RequestBody 驗證失敗，回傳 400 與中文錯誤清單 */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, Object>> handleValidation(MethodArgumentNotValidException ex) {
        List<String> errors = ex.getBindingResult().getAllErrors().stream()
                .map(e -> String.valueOf(e.getDefaultMessage())).toList();
        return ResponseEntity.badRequest()
                .body(Map.<String, Object>of("message", "資料驗證失敗", "errors", errors));
    }

    /** API：帳號重複、請假額度不足等商業規則錯誤 */
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, Object>> handleIllegal(IllegalArgumentException ex) {
        return ResponseEntity.badRequest()
                .body(Map.<String, Object>of("message", String.valueOf(ex.getMessage())));
    }
}
