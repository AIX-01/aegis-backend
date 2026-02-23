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

    // --- 새로운 통계 API용 쿼리 ---

    // 기간 내 총 이벤트 수
    @Query("SELECT COUNT(e) FROM Event e WHERE e.occurredAt BETWEEN :startDate AND :endDate")
    long countTotalEventsBetween(@Param("startDate") LocalDateTime startDate, @Param("endDate") LocalDateTime endDate);

    // 기간 내 카메라별 이벤트 분포
    @Query("SELECT e.camera.name, COUNT(e) FROM Event e WHERE e.occurredAt BETWEEN :startDate AND :endDate GROUP BY e.camera.name ORDER BY COUNT(e) DESC")
    List<Object[]> countCameraDistributionBetween(@Param("startDate") LocalDateTime startDate, @Param("endDate") LocalDateTime endDate);

    // 기간 내 이벤트 유형별 분포
    @Query("SELECT e.type, COUNT(e) FROM Event e WHERE e.occurredAt BETWEEN :startDate AND :endDate GROUP BY e.type")
    List<Object[]> countEventTypeDistributionBetween(@Param("startDate") LocalDateTime startDate, @Param("endDate") LocalDateTime endDate);

    // 기간 내 일별 이벤트 추이 (PostgreSQL)
    @Query(value = "SELECT DATE(e.occurred_at), COUNT(e), SUM(CASE WHEN e.status = 'ANALYZED' THEN 1 ELSE 0 END) " +
                   "FROM events e WHERE e.occurred_at BETWEEN :startDate AND :endDate " +
                   "GROUP BY DATE(e.occurred_at) ORDER BY DATE(e.occurred_at)", nativeQuery = true)
    List<Object[]> findPeriodTrendBetween(@Param("startDate") LocalDateTime startDate, @Param("endDate") LocalDateTime endDate);

    // 기간 내 분석 완료된 이벤트 수
    @Query("SELECT COUNT(e) FROM Event e WHERE e.status = 'ANALYZED' AND e.occurredAt BETWEEN :startDate AND :endDate")
    long countResolvedEventsBetween(@Param("startDate") LocalDateTime startDate, @Param("endDate") LocalDateTime endDate);

    // 기간 내 가장 많이 발생한 이벤트 유형
    @Query("SELECT e.type FROM Event e WHERE e.occurredAt BETWEEN :startDate AND :endDate GROUP BY e.type ORDER BY COUNT(e) DESC")
    List<String> findTopEventTypeBetween(@Param("startDate") LocalDateTime startDate, @Param("endDate") LocalDateTime endDate);

    // 기간 내 긴급 알림 수 (위험도: ABNORMAL, SUSPICIOUS)
    @Query("SELECT COUNT(e) FROM Event e WHERE e.risk IN ('ABNORMAL', 'SUSPICIOUS') AND e.occurredAt BETWEEN :startDate AND :endDate")
    long countAlertsBetween(@Param("startDate") LocalDateTime startDate, @Param("endDate") LocalDateTime endDate);


    // --- 기존 통계 API용 쿼리 (삭제 예정) ---

    @Query("SELECT e.type, COUNT(e) FROM Event e GROUP BY e.type")
    List<Object[]> countByEventType();

    @Query(value = "SELECT DATE(occurred_at) as date, COUNT(*) FROM events WHERE occurred_at >= :startDate GROUP BY DATE(occurred_at)", nativeQuery = true)
    List<Object[]> countByDateSince(@Param("startDate") LocalDateTime startDate);

    @Query(value = "SELECT DATE(occurred_at) as date, COUNT(*) FROM events WHERE occurred_at >= :startDate AND type IN ('ASSAULT', 'BURGLARY') GROUP BY DATE(occurred_at)", nativeQuery = true)
    List<Object[]> countAlertsByDateSince(@Param("startDate") LocalDateTime startDate);

    @Query(value = "SELECT EXTRACT(DOW FROM occurred_at) as day_of_week, COUNT(*), SUM(CASE WHEN status = 'ANALYZED' THEN 1 ELSE 0 END) FROM events WHERE occurred_at >= :startDate GROUP BY EXTRACT(DOW FROM occurred_at)", nativeQuery = true)
    List<Object[]> countByDayOfWeekSince(@Param("startDate") LocalDateTime startDate);
}
