package com.staff.demo.config;

import com.staff.demo.entity.Employee;
import com.staff.demo.repository.EmployeeRepository;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/** 使用臨時密碼登入的人，在變更密碼之前只能停留在個人資料頁 */
@Configuration
@RequiredArgsConstructor
public class WebConfig implements WebMvcConfigurer {

    private final EmployeeRepository employeeRepo;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(new HandlerInterceptor() {
            @Override
            public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler)
                    throws Exception {
                Authentication auth = SecurityContextHolder.getContext().getAuthentication();
                if (auth == null || auth instanceof AnonymousAuthenticationToken || !auth.isAuthenticated()) {
                    return true;
                }
                boolean mustChange = employeeRepo.findByUsername(auth.getName())
                        .map(Employee::isMustChangePassword).orElse(false);
                if (mustChange) {
                    response.sendRedirect(request.getContextPath() + "/front/profile");
                    return false;
                }
                return true;
            }
        }).excludePathPatterns(
                "/front/profile", "/front/profile/**", "/api/profile", "/api/profile/**",
                "/login", "/logout", "/forgot-password", "/error",
                "/css/**", "/js/**", "/swagger-ui.html", "/swagger-ui/**", "/v3/api-docs/**");
    }
}
