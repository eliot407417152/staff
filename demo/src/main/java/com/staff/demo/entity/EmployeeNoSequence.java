package com.staff.demo.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** 流水號計數器：每個「規則 + 部門 + 期間」一筆 */
@Entity
@Table(name = "employee_no_sequences")
@Getter
@Setter
@NoArgsConstructor
public class EmployeeNoSequence {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "seq_key", nullable = false, unique = true, length = 100)
    private String seqKey;

    @Column(name = "last_value", nullable = false)
    private long lastValue;

    public EmployeeNoSequence(String seqKey, long lastValue) {
        this.seqKey = seqKey;
        this.lastValue = lastValue;
    }
}
