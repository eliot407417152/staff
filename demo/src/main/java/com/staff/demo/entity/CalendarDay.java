package com.staff.demo.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;

/** 政府行政機關辦公日曆表的一天：holiday = true 放假，false 要上班（含補行上班日） */
@Entity
@Table(name = "calendar_days")
@Getter
@Setter
@NoArgsConstructor
public class CalendarDay {

    @Id
    @Column(name = "calendar_date")
    private LocalDate date;

    @Column(nullable = false)
    private boolean holiday;

    @Column(length = 100)
    private String note;

    public CalendarDay(LocalDate date, boolean holiday, String note) {
        this.date = date;
        this.holiday = holiday;
        this.note = note;
    }
}
