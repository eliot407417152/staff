package com.staff.demo.repository;

import com.staff.demo.entity.Employee;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface EmployeeRepository extends JpaRepository<Employee, Long> {
    Optional<Employee> findByUsername(String username);

    List<Employee> findByEmployeeNoIsNull();

    boolean existsByManager(Employee manager);

    List<Employee> findByEnabledTrueOrderByEmployeeNoAsc();
}
