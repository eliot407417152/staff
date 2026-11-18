package com.staff.demo.repository;

import com.staff.demo.entity.TyphoonDay;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;

public interface TyphoonDayRepository extends JpaRepository<TyphoonDay, LocalDate> {

    List<TyphoonDay> findByDateBetweenOrderByDateDesc(LocalDate from, LocalDate to);

    List<TyphoonDay> findAllByOrderByDateDesc();
}
