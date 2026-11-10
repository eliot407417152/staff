package com.staff.demo.config;

import com.staff.demo.entity.Department;
import com.staff.demo.entity.Employee;
import com.staff.demo.entity.EmployeeNoRule;
import com.staff.demo.entity.LeaveType;
import com.staff.demo.repository.DepartmentRepository;
import com.staff.demo.repository.EmployeeNoRuleRepository;
import com.staff.demo.repository.EmployeeRepository;
import com.staff.demo.repository.LeaveTypeRepository;
import com.staff.demo.service.EmployeeNoService;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.time.LocalDate;

/**
 * 啟動時補齊預設資料：預設部門、員工編號規則、假別、測試帳號，
 * 並替「還沒有員工編號」的舊帳號補上編號。正式上線前請移除測試帳號。
 */
@Component
@RequiredArgsConstructor
public class DataInitializer implements CommandLineRunner {

    private final EmployeeRepository employeeRepo;
    private final DepartmentRepository departmentRepo;
    private final EmployeeNoRuleRepository ruleRepo;
    private final LeaveTypeRepository leaveTypeRepo;
    private final EmployeeNoService noService;
    private final PasswordEncoder encoder;

    @Override
    public void run(String... args) {
        Department hq = departmentRepo.findByCode("HQ").orElseGet(() -> {
            Department d = new Department();
            d.setCode("HQ");
            d.setName("總公司");
            return departmentRepo.save(d);
        });

        if (ruleRepo.findByCode(EmployeeNoService.DEFAULT_RULE).isEmpty()) {
            EmployeeNoRule rule = new EmployeeNoRule();
            rule.setCode(EmployeeNoService.DEFAULT_RULE);   // 其餘欄位用預設：EMP + 西元年 + 4 碼流水號
            ruleRepo.save(rule);
        }

        seedLeaveTypes();

        if (employeeRepo.count() == 0) {
            Employee admin = employeeRepo.save(create("admin", "admin123", "系統管理員", "ADMIN", "系統管理員", hq));
            Employee emp = create("emp001", "123456", "王小明", "EMPLOYEE", "專員", hq);
            emp.setManager(admin);   // 測試用：emp001 的請假由 admin 審核
            employeeRepo.save(emp);
        }

        // 補員工編號（包含第一次升級前就存在的舊帳號）
        for (Employee e : employeeRepo.findByEmployeeNoIsNull()) {
            if (e.getDepartment() == null) {
                e.setDepartment(hq);
            }
            e.setEmployeeNo(noService.generate(e.getHireDate(), e.getDepartment()));
            employeeRepo.save(e);
        }
    }

    /** 預設假別。額度為每年上限（天 × 8 小時 × 60 分），null 代表不限，請依公司規定調整。 */
    private void seedLeaveTypes() {
        if (leaveTypeRepo.count() > 0) {
            return;
        }
        addType("ANNUAL", "特休", null);
        addType("PERSONAL", "事假", 14 * 8 * 60);
        addType("SICK", "病假", 30 * 8 * 60);
        addType("MARRIAGE", "婚假", 8 * 8 * 60);
        addType("BEREAVEMENT", "喪假", null);
        addType("OFFICIAL", "公假", null);
    }

    private void addType(String code, String name, Integer quotaMinutes) {
        LeaveType t = new LeaveType();
        t.setCode(code);
        t.setName(name);
        t.setAnnualQuotaMinutes(quotaMinutes);
        leaveTypeRepo.save(t);
    }

    private Employee create(String username, String rawPassword, String name,
                            String role, String jobTitle, Department dept) {
        Employee e = new Employee();
        e.setUsername(username);
        e.setPassword(encoder.encode(rawPassword));
        e.setName(name);
        e.setRole(role);
        e.setJobTitle(jobTitle);
        e.setDepartment(dept);
        e.setHireDate(LocalDate.now());
        return e;
    }
}
