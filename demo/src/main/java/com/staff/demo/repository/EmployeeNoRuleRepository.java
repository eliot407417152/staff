package com.staff.demo.repository;

import com.staff.demo.entity.EmployeeNoRule;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface EmployeeNoRuleRepository extends JpaRepository<EmployeeNoRule, Long> {
    Optional<EmployeeNoRule> findByCode(String code);
}
