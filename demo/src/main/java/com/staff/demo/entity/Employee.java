package com.staff.demo.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.ColumnDefault;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "employees")
@Getter
@Setter
@NoArgsConstructor
public class Employee {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 員工編號：依「員工編號規則」自動產生，建立後不再變動 */
    @Column(name = "employee_no", unique = true, length = 30)
    private String employeeNo;

    // ---- 帳號（後台管理） ----
    @Column(nullable = false, unique = true, length = 50)
    private String username;

    /** BCrypt 加密後的密碼，不要存明碼 */
    @Column(nullable = false)
    private String password;

    /** ADMIN 或 EMPLOYEE（Spring Security 會自動加上 ROLE_ 前綴） */
    @Column(nullable = false, length = 20)
    private String role;

    @Column(nullable = false)
    private boolean enabled = true;

    /** 使用系統產生的臨時密碼登入後，必須先變更密碼 */
    @ColumnDefault("false")
    @Column(name = "must_change_password", nullable = false)
    private boolean mustChangePassword = false;

    /** 最近一次「忘記密碼」重設的時間，用來限制申請頻率 */
    private LocalDateTime passwordResetAt;

    // ---- 任職資料（只有後台能改） ----
    @ManyToOne
    @JoinColumn(name = "department_id")
    private Department department;

    /** 直屬主管：請假申請由主管審核（沒有主管時由管理員審核） */
    @ManyToOne
    @JoinColumn(name = "manager_id")
    private Employee manager;

    @Column(length = 50)
    private String jobTitle;

    private LocalDate hireDate;

    // ---- 個人資料（員工本人也能改） ----
    @Column(nullable = false, length = 50)
    private String name;

    @Column(length = 100)
    private String email;

    @Column(length = 30)
    private String phone;

    private LocalDate birthday;

    @Column(length = 200)
    private String address;

    @Column(length = 50)
    private String emergencyContactName;

    @Column(length = 30)
    private String emergencyContactPhone;
}
