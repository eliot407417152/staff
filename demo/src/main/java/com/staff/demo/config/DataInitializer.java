package com.staff.demo.config;

import com.staff.demo.entity.Employee;
import com.staff.demo.repository.EmployeeRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

/** 第一次啟動、資料表是空的時候，建立兩組測試帳號。正式上線前請移除或改密碼。 */
@Component
@RequiredArgsConstructor
public class DataInitializer implements CommandLineRunner {

    private final EmployeeRepository repo;
    private final PasswordEncoder encoder;

    @Override
    public void run(String... args) {
        if (repo.count() > 0) {
            return;
        }
        repo.save(create("admin", "admin123", "系統管理員", "ADMIN"));
        repo.save(create("emp001", "123456", "王小明", "EMPLOYEE"));
    }

    private Employee create(String username, String rawPassword, String name, String role) {
        Employee e = new Employee();
        e.setUsername(username);
        e.setPassword(encoder.encode(rawPassword));
        e.setName(name);
        e.setRole(role);
        return e;
    }
}
