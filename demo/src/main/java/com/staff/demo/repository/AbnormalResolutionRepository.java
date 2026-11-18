package com.staff.demo.repository;

import com.staff.demo.entity.AbnormalResolution;
import com.staff.demo.entity.Employee;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface AbnormalResolutionRepository extends JpaRepository<AbnormalResolution, Long> {

    Optional<AbnormalResolution> findByEmployeeAndWorkDate(Employee employee, LocalDate workDate);

    List<AbnormalResolution> findByWorkDateBetweenAndActiveTrue(LocalDate from, LocalDate to);
}
