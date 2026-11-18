package com.staff.demo.repository;

import com.staff.demo.entity.AttendanceRecord;
import com.staff.demo.entity.Employee;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface AttendanceRepository extends JpaRepository<AttendanceRecord, Long> {

    Optional<AttendanceRecord> findByEmployeeAndWorkDate(Employee employee, LocalDate workDate);

    List<AttendanceRecord> findTop10ByEmployeeOrderByWorkDateDesc(Employee employee);

    List<AttendanceRecord> findByWorkDateOrderByClockInAsc(LocalDate workDate);

    List<AttendanceRecord> findByEmployeeAndWorkDateBetween(Employee employee, LocalDate from, LocalDate to);

    List<AttendanceRecord> findByWorkDateBetween(LocalDate from, LocalDate to);
}
