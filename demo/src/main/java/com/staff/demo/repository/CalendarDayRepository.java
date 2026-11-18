package com.staff.demo.repository;

import com.staff.demo.entity.CalendarDay;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;

public interface CalendarDayRepository extends JpaRepository<CalendarDay, LocalDate> {

    List<CalendarDay> findByDateBetweenOrderByDateAsc(LocalDate from, LocalDate to);

    long countByDateBetween(LocalDate from, LocalDate to);
}
