package com.staff.demo.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;
import org.springframework.format.annotation.DateTimeFormat;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;

/**
 * 請假申請：先選假別與「要請幾個小時」，再選開始日期與時間，結束時間由系統依上班時間、午休、假日自動算出。
 */
@Getter
@Setter
public class LeaveRequestForm {

    @NotNull(message = "請選擇假別")
    private Long leaveTypeId;

    /** 請假時數（小時），以 0.5 小時為單位 */
    @NotNull(message = "請輸入請假時數")
    @DecimalMin(value = "0.5", message = "請假時數至少 0.5 小時")
    @DecimalMax(value = "240", message = "單次請假不可超過 240 小時（30 個上班日），請分開申請")
    private BigDecimal hours;

    @NotNull(message = "請選擇開始日期")
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate startDate;

    /** 開始時間，例如 14:00；只能選上班時段內的整點或半點 */
    @NotNull(message = "請選擇開始時間")
    @DateTimeFormat(pattern = "HH:mm")
    private LocalTime startTime;

    @NotBlank(message = "請填寫請假事由")
    @Size(max = 200, message = "事由最多 200 字")
    private String reason;
}
