package com.staff.demo.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class PasswordChangeForm {

    @NotBlank(message = "請輸入目前密碼")
    private String currentPassword;

    @NotBlank(message = "請輸入新密碼")
    @Size(min = 8, max = 50, message = "新密碼長度需為 8～50 字元")
    private String newPassword;

    @NotBlank(message = "請再次輸入新密碼")
    private String confirmPassword;
}
