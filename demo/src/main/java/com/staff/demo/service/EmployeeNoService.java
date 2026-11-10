package com.staff.demo.service;

import com.staff.demo.dto.EmployeeNoRuleForm;
import com.staff.demo.entity.Department;
import com.staff.demo.entity.EmployeeNoRule;
import com.staff.demo.entity.EmployeeNoSequence;
import com.staff.demo.repository.EmployeeNoRuleRepository;
import com.staff.demo.repository.EmployeeNoSequenceRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

/** 依「員工編號規則」產生編號：前綴 + [部門代碼] + [日期] + 流水號 */
@Service
@RequiredArgsConstructor
public class EmployeeNoService {

    public static final String DEFAULT_RULE = "DEFAULT";

    private final EmployeeNoRuleRepository ruleRepo;
    private final EmployeeNoSequenceRepository seqRepo;

    public EmployeeNoRule getRule() {
        return ruleRepo.findByCode(DEFAULT_RULE).orElseThrow();
    }

    /** 產生並「使用」下一個編號（流水號會 +1），要和新增員工放在同一個交易裡 */
    @Transactional
    public String generate(LocalDate hireDate, Department dept) {
        return build(getRule(), hireDate, dept, true);
    }

    /** 只預覽下一個編號，不會消耗流水號 */
    @Transactional(readOnly = true)
    public String preview(EmployeeNoRule rule, LocalDate hireDate, Department dept) {
        return build(rule, hireDate, dept, false);
    }

    @Transactional
    public void updateRule(EmployeeNoRuleForm f) {
        EmployeeNoRule r = getRule();
        r.setPrefix(f.getPrefix() == null ? "" : f.getPrefix().trim().toUpperCase());
        r.setIncludeDepartmentCode(f.isIncludeDepartmentCode());
        r.setDateFormat(f.getDateFormat() == null ? "" : f.getDateFormat());
        r.setDateBasis(f.getDateBasis());
        r.setSeqLength(f.getSeqLength());
        r.setResetPeriod(f.getResetPeriod());
        ruleRepo.save(r);
    }

    private String build(EmployeeNoRule rule, LocalDate hireDate, Department dept, boolean consume) {
        LocalDate base = (rule.getDateBasis() == EmployeeNoRule.DateBasis.HIRE_DATE && hireDate != null)
                ? hireDate : LocalDate.now();
        String deptCode = (rule.isIncludeDepartmentCode() && dept != null) ? dept.getCode() : "";

        String period = switch (rule.getResetPeriod()) {
            case YEARLY -> String.valueOf(base.getYear());
            case MONTHLY -> base.format(DateTimeFormatter.ofPattern("yyyyMM"));
            case NEVER -> "ALL";
        };
        String key = rule.getCode() + "|" + deptCode + "|" + period;

        long seq;
        if (consume) {
            EmployeeNoSequence row = seqRepo.findBySeqKeyForUpdate(key)
                    .orElseGet(() -> seqRepo.save(new EmployeeNoSequence(key, 0L)));
            row.setLastValue(row.getLastValue() + 1);
            seq = seqRepo.save(row).getLastValue();
        } else {
            seq = seqRepo.findBySeqKey(key).map(EmployeeNoSequence::getLastValue).orElse(0L) + 1;
        }

        String datePart = rule.getDateFormat().isBlank()
                ? "" : base.format(DateTimeFormatter.ofPattern(rule.getDateFormat()));
        return rule.getPrefix() + deptCode + datePart + String.format("%0" + rule.getSeqLength() + "d", seq);
    }
}
