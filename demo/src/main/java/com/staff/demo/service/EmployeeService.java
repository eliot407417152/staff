package com.staff.demo.service;

import com.staff.demo.dto.EmployeeForm;
import com.staff.demo.dto.ProfileForm;
import com.staff.demo.entity.Department;
import com.staff.demo.entity.Employee;
import com.staff.demo.repository.DepartmentRepository;
import com.staff.demo.repository.EmployeeRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.util.List;

@Service
@RequiredArgsConstructor
public class EmployeeService {

    /** API 回傳用的員工資料（不含密碼） */
    public record EmployeeView(
            Long id, String employeeNo, String username, String name, String role, boolean enabled,
            Long departmentId, String departmentName, String jobTitle, LocalDate hireDate,
            String email, String phone, LocalDate birthday, String address,
            String emergencyContactName, String emergencyContactPhone,
            Long managerId, String managerName) {}

    private final EmployeeRepository employeeRepo;
    private final DepartmentRepository departmentRepo;
    private final EmployeeNoService employeeNoService;
    private final PasswordEncoder passwordEncoder;

    public EmployeeView toView(Employee e) {
        Department d = e.getDepartment();
        Employee m = e.getManager();
        return new EmployeeView(e.getId(), e.getEmployeeNo(), e.getUsername(), e.getName(), e.getRole(),
                e.isEnabled(), d == null ? null : d.getId(), d == null ? null : d.getName(),
                e.getJobTitle(), e.getHireDate(), e.getEmail(), e.getPhone(), e.getBirthday(),
                e.getAddress(), e.getEmergencyContactName(), e.getEmergencyContactPhone(),
                m == null ? null : m.getId(), m == null ? null : m.getName());
    }

    public List<Employee> findAll() {
        return employeeRepo.findAll(Sort.by("employeeNo"));
    }

    /** 在職員工（供選擇直屬主管） */
    public List<Employee> findActive() {
        return employeeRepo.findByEnabledTrueOrderByEmployeeNoAsc();
    }

    public Employee get(Long id) {
        return employeeRepo.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "找不到員工"));
    }

    public Employee getByUsername(String username) {
        return employeeRepo.findByUsername(username).orElseThrow();
    }

    /** 後台新增員工：自動產生員工編號 */
    @Transactional
    public Employee create(EmployeeForm f) {
        String username = f.getUsername().trim();
        if (employeeRepo.findByUsername(username).isPresent()) {
            throw new IllegalArgumentException("此帳號已存在");
        }
        Employee e = new Employee();
        e.setUsername(username);
        e.setPassword(passwordEncoder.encode(f.getPassword()));
        e.setMustChangePassword(true);   // 管理員設定的初始密碼，員工第一次登入必須自行變更
        e.setRole(f.getRole());
        e.setEnabled(f.isEnabled());
        applyEmployment(e, f);
        applyContact(e, f);
        e.setEmployeeNo(employeeNoService.generate(e.getHireDate(), e.getDepartment()));
        return employeeRepo.save(e);
    }

    /** 後台編輯員工。帳號與員工編號不可修改。 */
    @Transactional
    public Employee update(Long id, EmployeeForm f, String currentUsername) {
        Employee e = get(id);
        // 管理員不能把自己降級或停用，避免把自己鎖在系統外
        if (!e.getUsername().equals(currentUsername)) {
            e.setRole(f.getRole());
            e.setEnabled(f.isEnabled());
        }
        if (f.getPassword() != null && !f.getPassword().isBlank()) {
            e.setPassword(passwordEncoder.encode(f.getPassword()));
            // 管理員幫別人重設的密碼視為臨時密碼，對方登入後必須自行變更
            e.setMustChangePassword(!e.getUsername().equals(currentUsername));
        }
        applyEmployment(e, f);
        applyContact(e, f);
        if (e.getEmployeeNo() == null) {
            e.setEmployeeNo(employeeNoService.generate(e.getHireDate(), e.getDepartment()));
        }
        return employeeRepo.save(e);
    }

    /** 員工本人修改個人資料：只會動到 ProfileForm 有的欄位 */
    @Transactional
    public void updateProfile(String username, ProfileForm f) {
        Employee e = getByUsername(username);
        applyContact(e, f);
        employeeRepo.save(e);
    }

    private void applyEmployment(Employee e, EmployeeForm f) {
        Department dept = f.getDepartmentId() == null ? null
                : departmentRepo.findById(f.getDepartmentId()).orElseThrow();
        e.setDepartment(dept);

        Employee manager = null;
        if (f.getManagerId() != null) {
            manager = employeeRepo.findById(f.getManagerId())
                    .orElseThrow(() -> new IllegalArgumentException("找不到指定的主管"));
            if (manager.getId().equals(e.getId())) {
                throw new IllegalArgumentException("主管不能是自己");
            }
        }
        e.setManager(manager);

        e.setJobTitle(blankToNull(f.getJobTitle()));
        e.setHireDate(f.getHireDate());
    }

    private void applyContact(Employee e, ProfileForm f) {
        e.setName(f.getName().trim());
        e.setEmail(blankToNull(f.getEmail()));
        e.setPhone(blankToNull(f.getPhone()));
        e.setBirthday(f.getBirthday());
        e.setAddress(blankToNull(f.getAddress()));
        e.setEmergencyContactName(blankToNull(f.getEmergencyContactName()));
        e.setEmergencyContactPhone(blankToNull(f.getEmergencyContactPhone()));
    }

    private String blankToNull(String s) {
        return (s == null || s.isBlank()) ? null : s.trim();
    }
}
