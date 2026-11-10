package com.staff.demo.dto;

import com.staff.demo.entity.EmployeeNoRule;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class EmployeeNoRuleForm {

    @Pattern(regexp = "[A-Za-z0-9]{0,10}", message = "前綴限英數字，最多 10 碼")
    private String prefix;

    private boolean includeDepartmentCode;

    @Pattern(regexp = "^(|yyyy|yy|yyyyMM|yyMM)$", message = "日期格式不正確")
    private String dateFormat;

    @NotNull(message = "請選擇日期依據")
    private EmployeeNoRule.DateBasis dateBasis;

    @Min(value = 1, message = "流水號至少 1 碼")
    @Max(value = 10, message = "流水號最多 10 碼")
    private int seqLength;

    @NotNull(message = "請選擇歸零週期")
    private EmployeeNoRule.ResetPeriod resetPeriod;

    public static EmployeeNoRuleForm of(EmployeeNoRule r) {
        EmployeeNoRuleForm f = new EmployeeNoRuleForm();
        f.prefix = r.getPrefix();
        f.includeDepartmentCode = r.isIncludeDepartmentCode();
        f.dateFormat = r.getDateFormat();
        f.dateBasis = r.getDateBasis();
        f.seqLength = r.getSeqLength();
        f.resetPeriod = r.getResetPeriod();
        return f;
    }
}
