package com.staff.demo.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 颱風假（天然災害停班日），由管理員登記。
 * 公司規定颱風假不支薪：員工可用特休補上，否則視為天災假並扣薪。
 */
@Entity
@Table(name = "typhoon_days")
@Getter
@Setter
@NoArgsConstructor
public class TyphoonDay {

    @Id
    @Column(name = "typhoon_date")
    private LocalDate date;

    @Column(length = 100)
    private String note;

    @Column(length = 50)
    private String createdBy;

    @Column(nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();
}
