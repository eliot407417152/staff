package com.staff.demo.repository;

import com.staff.demo.entity.LeaveType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface LeaveTypeRepository extends JpaRepository<LeaveType, Long> {
    List<LeaveType> findByActiveTrueOrderByIdAsc();

    Optional<LeaveType> findByCode(String code);
}
