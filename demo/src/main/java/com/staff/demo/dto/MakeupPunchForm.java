package com.staff.demo.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDate;
import java.time.LocalTime;

@Getter
@Setter
public class MakeupPunchForm {

    @NotNull(message = "請選擇要補打卡的日期")
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate workDate;

    /** 上班時間，沒有要補就留空 */
    @DateTimeFormat(iso = DateTimeFormat.ISO.TIME)
    private LocalTime clockIn;

    /** 下班時間，沒有要補就留空 */
    @DateTimeFormat(iso = DateTimeFormat.ISO.TIME)
    private LocalTime clockOut;

    @NotBlank(message = "請填寫補打卡事由")
    @Size(max = 200, message = "事由最多 200 字")
    private String reason;
}
