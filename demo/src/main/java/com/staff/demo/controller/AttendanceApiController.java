package com.staff.demo.controller;

import com.staff.demo.service.AttendanceService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/attendance")
@RequiredArgsConstructor
@Tag(name = "打卡")
public class AttendanceApiController {

    private final AttendanceService service;

    /** 回傳用的資料格式，不直接回傳 Entity，避免把密碼等欄位送出去 */
    public record AttendanceDto(String name, LocalDate workDate,
                                LocalDateTime clockIn, LocalDateTime clockOut) {}

    @Operation(summary = "查詢我最近 10 筆打卡紀錄")
    @GetMapping("/recent")
    public List<AttendanceDto> recent(Authentication auth) {
        return service.recent(auth.getName()).stream()
                .map(r -> new AttendanceDto(r.getEmployee().getName(),
                        r.getWorkDate(), r.getClockIn(), r.getClockOut()))
                .toList();
    }

    @Operation(summary = "上班打卡")
    @PostMapping("/clock-in")
    public Map<String, String> clockIn(Authentication auth) {
        return Map.of("message", service.clockIn(auth.getName()));
    }

    @Operation(summary = "下班打卡")
    @PostMapping("/clock-out")
    public Map<String, String> clockOut(Authentication auth) {
        return Map.of("message", service.clockOut(auth.getName()));
    }
}