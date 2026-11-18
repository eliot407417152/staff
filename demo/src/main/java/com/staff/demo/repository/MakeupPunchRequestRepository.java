package com.staff.demo.repository;

import com.staff.demo.entity.Employee;
import com.staff.demo.entity.MakeupPunchRequest;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;

public interface MakeupPunchRequestRepository extends JpaRepository<MakeupPunchRequest, Long> {

    List<MakeupPunchRequest> findByEmployeeOrderByWorkDateDescCreatedAtDesc(Employee employee);

    List<MakeupPunchRequest> findByStatusOrderByCreatedAtAsc(MakeupPunchRequest.Status status);

    List<MakeupPunchRequest> findByStatusAndEmployeeManagerOrderByCreatedAtAsc(MakeupPunchRequest.Status status,
                                                                              Employee manager);

    List<MakeupPunchRequest> findAllByOrderByCreatedAtDesc();

    List<MakeupPunchRequest> findByStatusOrderByCreatedAtDesc(MakeupPunchRequest.Status status);

    boolean existsByEmployeeAndWorkDateAndStatus(Employee employee, LocalDate workDate,
                                                 MakeupPunchRequest.Status status);
}
