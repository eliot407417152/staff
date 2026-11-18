package com.staff.demo.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

/** 補打卡申請：員工忘記打卡時，補填上班／下班時間，由主管或管理員審核 */
@Entity
@Table(name = "makeup_punch_requests")
@Getter
@Setter
@NoArgsConstructor
public class MakeupPunchRequest {

    public enum Status {
        PENDING("審核中"), APPROVED("已核准"), REJECTED("已駁回"), CANCELLED("已取消"), VOIDED("已註銷");

        private final String label;

        Status(String label) { this.label = label; }

        public String getLabel() { return label; }
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "employee_id")
    private Employee employee;

    @Column(nullable = false)
    private LocalDate workDate;

    /** 要補的上班時間（可空，代表只補下班） */
    private LocalTime clockInTime;

    /** 要補的下班時間（可空，代表只補上班） */
    private LocalTime clockOutTime;

    @Column(nullable = false, length = 200)
    private String reason;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Status status = Status.PENDING;

    @ManyToOne
    @JoinColumn(name = "decided_by_id")
    private Employee decidedBy;

    private LocalDateTime decidedAt;

    @Column(length = 200)
    private String decisionComment;

    // ---- 核准時記下原本的打卡紀錄，註銷時用來還原 ----

    /** 核准時這天原本沒有打卡紀錄（整筆是補打卡建立的） */
    @Column(nullable = false)
    private boolean recordCreated = false;

    private LocalDateTime prevClockIn;

    private LocalDateTime prevClockOut;

    // ---- 註銷（管理員撤銷已核准的補打卡） ----

    @ManyToOne
    @JoinColumn(name = "voided_by_id")
    private Employee voidedBy;

    private LocalDateTime voidedAt;

    @Column(length = 200)
    private String voidReason;

    @Column(nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();
}
