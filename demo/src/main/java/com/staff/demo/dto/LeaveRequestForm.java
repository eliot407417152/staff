package com.staff.demo.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDateTime;

@Getter
@Setter
public class LeaveRequestForm {

    @NotNull(message = "請選擇假別")
    private Long leaveTypeId;

    @NotNull(message = "請輸入開始時間")
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
    private LocalDateTime startAt;

    @NotNull(message = "請輸入結束時間")
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
    private LocalDateTime endAt;

    @NotBlank(message = "請填寫請假事由")
    @Size(max = 200, message = "事由最多 200 字")
    private String reason;
}
