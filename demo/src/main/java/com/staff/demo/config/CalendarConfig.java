package com.staff.demo.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/** 啟用排程：辦公日曆表定期自動同步 */
@Configuration
@EnableScheduling
public class CalendarConfig {
}
