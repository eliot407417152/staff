package com.staff.demo.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 員工編號規則。編號 = 前綴 + [部門代碼] + [日期] + 流水號
 * 例：EMP + 2026 + 0001 → EMP20260001
 */
@Entity
@Table(name = "employee_no_rules")
@Getter
@Setter
@NoArgsConstructor
public class EmployeeNoRule {

    /** 日期要以什麼為依據 */
    public enum DateBasis { HIRE_DATE, TODAY }

    /** 流水號何時歸零 */
    public enum ResetPeriod { YEARLY, MONTHLY, NEVER }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 30)
    private String code;

    @Column(nullable = false, length = 10)
    private String prefix = "EMP";

    @Column(nullable = false)
    private boolean includeDepartmentCode = false;

    /** 日期格式：空字串（不含日期）、yyyy、yy、yyyyMM、yyMM */
    @Column(nullable = false, length = 10)
    private String dateFormat = "yyyy";

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private DateBasis dateBasis = DateBasis.HIRE_DATE;

    @Column(nullable = false)
    private int seqLength = 4;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ResetPeriod resetPeriod = ResetPeriod.YEARLY;
}
