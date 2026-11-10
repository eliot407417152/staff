package com.staff.demo.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Table(name = "leave_requests")
@Getter
@Setter
@NoArgsConstructor
public class LeaveRequest {

    public enum Status {
        PENDING("審核中"), APPROVED("已核准"), REJECTED("已駁回"), CANCELLED("已取消");

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

    @ManyToOne(optional = false)
    @JoinColumn(name = "leave_type_id")
    private LeaveType leaveType;

    @Column(nullable = false)
    private LocalDateTime startAt;

    @Column(nullable = false)
    private LocalDateTime endAt;

    /** 實際請假分鐘數（已扣除週末與午休） */
    @Column(nullable = false)
    private int minutes;

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

    @Column(nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    public double getHours() {
        return minutes / 60.0;
    }

    /** 審核中，或已核准但還沒開始的請假，申請人可以取消 */
    public boolean isCancellable() {
        return status == Status.PENDING
                || (status == Status.APPROVED && startAt.isAfter(LocalDateTime.now()));
    }
}
