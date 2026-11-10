package com.staff.demo.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ForgotPasswordForm {

    @NotBlank(message = "請輸入帳號")
    private String username;

    @NotBlank(message = "請輸入 Email")
    @Email(message = "Email 格式不正確")
    private String email;
}
