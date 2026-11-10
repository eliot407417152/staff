package com.staff.demo.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/** 只處理 @RestController 的錯誤，讓 API 回傳清楚的 JSON 錯誤訊息 */
@RestControllerAdvice(annotations = RestController.class)
public class ApiExceptionHandler {

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, Object>> validation(MethodArgumentNotValidException ex) {
        List<String> errors = ex.getBindingResult().getAllErrors().stream()
                .map(e -> String.valueOf(e.getDefaultMessage())).toList();
        return ResponseEntity.badRequest()
                .body(Map.<String, Object>of("message", "資料驗證失敗", "errors", errors));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, Object>> illegal(IllegalArgumentException ex) {
        return ResponseEntity.badRequest()
                .body(Map.<String, Object>of("message", String.valueOf(ex.getMessage())));
    }
}
