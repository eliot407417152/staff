package com.staff.demo.config;

import com.staff.demo.repository.EmployeeRepository;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
public class SecurityConfig {

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/login", "/forgot-password", "/css/**", "/js/**",
                                "/swagger-ui.html", "/swagger-ui/**", "/v3/api-docs/**").permitAll()
                        .requestMatchers("/admin/**").hasRole("ADMIN")   // 後台：只有管理員
                        .anyRequest().authenticated())                   // 前台：登入後即可
                .formLogin(form -> form
                        .loginPage("/login")
                        .defaultSuccessUrl("/", true)
                        .permitAll())
                .logout(logout -> logout
                        .logoutSuccessUrl("/login?logout")
                        .permitAll())
                .csrf(csrf -> csrf.ignoringRequestMatchers("/api/**"));
        return http.build();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    /** 登入時從資料庫的 employees 表查帳號 */
    @Bean
    public UserDetailsService userDetailsService(EmployeeRepository repo) {
        return username -> repo.findByUsername(username)
                .map(e -> User.withUsername(e.getUsername())
                        .password(e.getPassword())
                        .roles(e.getRole())
                        .disabled(!e.isEnabled())
                        .build())
                .orElseThrow(() -> new UsernameNotFoundException("找不到帳號：" + username));
    }
}
