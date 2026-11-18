package com.staff.demo.dto;

import lombok.Getter;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

/** 單日出勤顯示資料：打卡時間 + 依公司規定算出的工時與狀態 */
@Getter
public class AttendanceDayView {

    private final LocalDate workDate;
    private final LocalDateTime clockIn;
    private final LocalDateTime clockOut;
    /** 實際上班時數（已扣午休與請假時段），格式 H:mm */
    private final String worked;
    /** 已核准請假時數，格式 H:mm */
    private final String leave;
    /** 還差多少才滿 8 小時，格式 H:mm；不足才有值 */
    private final String shortfall;
    private final String statusLabel;
    private final String statusBadge;
    /** 今天最早可下班的時間（上班中才有） */
    private final LocalTime earliestOut;

    public AttendanceDayView(LocalDate workDate, LocalDateTime clockIn, LocalDateTime clockOut,
                             String worked, String leave, String shortfall,
                             String statusLabel, String statusBadge, LocalTime earliestOut) {
        this.workDate = workDate;
        this.clockIn = clockIn;
        this.clockOut = clockOut;
        this.worked = worked;
        this.leave = leave;
        this.shortfall = shortfall;
        this.statusLabel = statusLabel;
        this.statusBadge = statusBadge;
        this.earliestOut = earliestOut;
    }
}
