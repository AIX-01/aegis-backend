package com.aegis.aegisbackend.domain.event.repository;

import com.aegis.aegisbackend.domain.event.entity.Event;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Repository
public interface EventRepository extends JpaRepository<Event, UUID>, JpaSpecificationExecutor<Event> {

    @Query("SELECT e.type, COUNT(e) FROM Event e GROUP BY e.type")
    List<Object[]> countByEventType();

    // PostgreSQL: DATE(occurred_at)로 날짜별 집계
    @Query(value = "SELECT DATE(occurred_at) as date, COUNT(*) FROM events WHERE occurred_at >= :startDate GROUP BY DATE(occurred_at)", nativeQuery = true)
    List<Object[]> countByDateSince(@Param("startDate") LocalDateTime startDate);

    // PostgreSQL: 날짜별 심각 이벤트(ASSAULT, BURGLARY) 집계
    @Query(value = "SELECT DATE(occurred_at) as date, COUNT(*) FROM events WHERE occurred_at >= :startDate AND type IN ('ASSAULT', 'BURGLARY') GROUP BY DATE(occurred_at)", nativeQuery = true)
    List<Object[]> countAlertsByDateSince(@Param("startDate") LocalDateTime startDate);

    // PostgreSQL: EXTRACT(DOW FROM ...)로 요일별 집계 (0=일요일, 6=토요일)
    @Query(value = "SELECT EXTRACT(DOW FROM occurred_at) as day_of_week, COUNT(*), SUM(CASE WHEN status = 'ANALYZED' THEN 1 ELSE 0 END) FROM events WHERE occurred_at >= :startDate GROUP BY EXTRACT(DOW FROM occurred_at)", nativeQuery = true)
    List<Object[]> countByDayOfWeekSince(@Param("startDate") LocalDateTime startDate);
}
