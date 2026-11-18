package com.staff.demo.repository;

import com.staff.demo.entity.Employee;
import com.staff.demo.entity.LeaveRequest;
import com.staff.demo.entity.LeaveType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;

public interface LeaveRequestRepository extends JpaRepository<LeaveRequest, Long> {

    List<LeaveRequest> findByEmployeeOrderByStartAtDesc(Employee employee);

    List<LeaveRequest> findByStatusOrderByCreatedAtAsc(LeaveRequest.Status status);

    List<LeaveRequest> findByStatusAndEmployeeManagerOrderByCreatedAtAsc(LeaveRequest.Status status, Employee manager);

    List<LeaveRequest> findAllByOrderByCreatedAtDesc();

    List<LeaveRequest> findByStatusOrderByCreatedAtDesc(LeaveRequest.Status status);

    /** 同一個人在這段時間內，是否已有審核中或已核准的請假 */
    @Query("select count(r) from LeaveRequest r where r.employee = :e and r.status in :st "
            + "and r.startAt < :end and r.endAt > :start")
    long countOverlap(@Param("e") Employee e, @Param("st") Collection<LeaveRequest.Status> st,
                      @Param("start") LocalDateTime start, @Param("end") LocalDateTime end);

    /** 某人、某假別、某期間內的請假分鐘數合計 */
    @Query("select coalesce(sum(r.minutes), 0) from LeaveRequest r where r.employee = :e and r.leaveType = :t "
            + "and r.status in :st and r.startAt >= :from and r.startAt < :to")
    Long sumMinutes(@Param("e") Employee e, @Param("t") LeaveType t,
                    @Param("st") Collection<LeaveRequest.Status> st,
                    @Param("from") LocalDateTime from, @Param("to") LocalDateTime to);

    /** 某人在這段時間內有重疊的請假（指定狀態） */
    @Query("select r from LeaveRequest r join fetch r.leaveType where r.employee = :e and r.status in :st "
            + "and r.startAt < :end and r.endAt > :start")
    List<LeaveRequest> findOverlapping(@Param("e") Employee e, @Param("st") Collection<LeaveRequest.Status> st,
                                       @Param("start") LocalDateTime start, @Param("end") LocalDateTime end);

    /** 所有人在這段時間內有重疊的請假（指定狀態），供後台報表使用 */
    @Query("select r from LeaveRequest r join fetch r.leaveType join fetch r.employee where r.status in :st "
            + "and r.startAt < :end and r.endAt > :start")
    List<LeaveRequest> findAllOverlapping(@Param("st") Collection<LeaveRequest.Status> st,
                                          @Param("start") LocalDateTime start, @Param("end") LocalDateTime end);
}
