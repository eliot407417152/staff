package com.staff.demo.dto;

import com.staff.demo.entity.Employee;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDate;

/** 後台新增／編輯員工用，包含個人資料與帳號、任職資料 */
@Getter
@Setter
public class EmployeeForm extends ProfileForm {

    @NotBlank(message = "帳號必填")
    @Pattern(regexp = "[A-Za-z0-9_.\\-]{3,50}", message = "帳號限英數字與 _ . -，長度 3～50")
    private String username;

    @Pattern(regexp = "^$|.{6,50}", message = "密碼需 6～50 字元（編輯時留空代表不變更）")
    private String password;

    @NotBlank(message = "請選擇角色")
    @Pattern(regexp = "ADMIN|EMPLOYEE", message = "角色不正確")
    private String role = "EMPLOYEE";

    private boolean enabled = true;

    private Long departmentId;

    /** 直屬主管（請假審核人） */
    private Long managerId;

    @Size(max = 50, message = "職稱最多 50 字")
    private String jobTitle;

    @NotNull(message = "到職日必填")
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate hireDate = LocalDate.now();

    public static EmployeeForm fromEmployee(Employee e) {
        EmployeeForm f = new EmployeeForm();
        f.fillFrom(e);
        f.username = e.getUsername();
        f.role = e.getRole();
        f.enabled = e.isEnabled();
        f.departmentId = e.getDepartment() == null ? null : e.getDepartment().getId();
        f.managerId = e.getManager() == null ? null : e.getManager().getId();
        f.jobTitle = e.getJobTitle();
        f.hireDate = e.getHireDate();
        return f;
    }
}
