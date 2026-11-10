package com.staff.demo.repository;

import com.staff.demo.entity.EmployeeNoSequence;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface EmployeeNoSequenceRepository extends JpaRepository<EmployeeNoSequence, Long> {

    Optional<EmployeeNoSequence> findBySeqKey(String seqKey);

    /** 鎖住這一列再加一，避免兩個人同時新增員工拿到同一個編號 */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from EmployeeNoSequence s where s.seqKey = :key")
    Optional<EmployeeNoSequence> findBySeqKeyForUpdate(@Param("key") String key);
}
