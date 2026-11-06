package com.staff.demo.service;

import com.staff.demo.entity.AttendanceRecord;
import com.staff.demo.entity.Employee;
import com.staff.demo.repository.AttendanceRepository;
import com.staff.demo.repository.EmployeeRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class AttendanceService {

    private final EmployeeRepository employeeRepo;
    private final AttendanceRepository attendanceRepo;

    public Employee getEmployee(String username) {
        return employeeRepo.findByUsername(username).orElseThrow();
    }

    public Optional<AttendanceRecord> findToday(String username) {
        return attendanceRepo.findByEmployeeAndWorkDate(getEmployee(username), LocalDate.now());
    }

    public List<AttendanceRecord> recent(String username) {
        return attendanceRepo.findTop10ByEmployeeOrderByWorkDateDesc(getEmployee(username));
    }

    public List<AttendanceRecord> todayAll() {
        return attendanceRepo.findByWorkDateOrderByClockInAsc(LocalDate.now());
    }

    /** 回傳要顯示給使用者的訊息 */
    @Transactional
    public String clockIn(String username) {
        Employee employee = getEmployee(username);
        LocalDate today = LocalDate.now();
        if (attendanceRepo.findByEmployeeAndWorkDate(employee, today).isPresent()) {
            return "今天已經打過上班卡了。";
        }
        AttendanceRecord record = new AttendanceRecord();
        record.setEmployee(employee);
        record.setWorkDate(today);
        record.setClockIn(LocalDateTime.now());
        attendanceRepo.save(record);
        return "上班打卡成功。";
    }

    @Transactional
    public String clockOut(String username) {
        Optional<AttendanceRecord> found = findToday(username);
        if (found.isEmpty()) {
            return "請先打上班卡。";
        }
        AttendanceRecord record = found.get();
        if (record.getClockOut() != null) {
            return "今天已經打過下班卡了。";
        }
        record.setClockOut(LocalDateTime.now());
        attendanceRepo.save(record);
        return "下班打卡成功。";
    }
}
