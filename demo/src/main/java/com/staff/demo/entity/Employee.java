package com.staff.demo.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "employees")
@Getter
@Setter
@NoArgsConstructor
public class Employee {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 50)
    private String username;

    /** BCrypt 加密後的密碼，不要存明碼 */
    @Column(nullable = false)
    private String password;

    @Column(nullable = false, length = 50)
    private String name;

    /** ADMIN 或 EMPLOYEE（Spring Security 會自動加上 ROLE_ 前綴） */
    @Column(nullable = false, length = 20)
    private String role;

    @Column(nullable = false)
    private boolean enabled = true;
}
