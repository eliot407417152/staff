package com.staff.demo.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class DepartmentForm {

    @NotBlank(message = "部門代碼必填")
    @Pattern(regexp = "[A-Za-z0-9]{1,10}", message = "部門代碼限英數字，最多 10 碼")
    private String code;

    @NotBlank(message = "部門名稱必填")
    @Size(max = 50, message = "部門名稱最多 50 字")
    private String name;
}
