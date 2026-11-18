package com.staff.demo.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 異常出勤的處理紀錄：管理員確認「其實有來上班／這筆異常不成立」後註銷。
 * 註銷只是標記，原始打卡資料不會被改動；也可以撤銷註銷（active 改成 false）。
 */
@Entity
@Table(name = "abnormal_resolutions",
        uniqueConstraints = @UniqueConstraint(columnNames = {"employee_id", "work_date"}))
@Getter
@Setter
@NoArgsConstructor
public class AbnormalResolution {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "employee_id")
    private Employee employee;

    @Column(name = "work_date", nullable = false)
    private LocalDate workDate;

    @Column(nullable = false, length = 200)
    private String reason;

    @ManyToOne(optional = false)
    @JoinColumn(name = "resolved_by_id")
    private Employee resolvedBy;

    @Column(nullable = false)
    private LocalDateTime resolvedAt = LocalDateTime.now();

    /** true＝目前有效（異常已註銷）；false＝已撤銷註銷，異常重新出現 */
    @Column(nullable = false)
    private boolean active = true;

    @ManyToOne
    @JoinColumn(name = "revoked_by_id")
    private Employee revokedBy;

    private LocalDateTime revokedAt;
}
