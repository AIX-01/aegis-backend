package com.aegis.aegisbackend.domain.event.repository;

import com.aegis.aegisbackend.domain.event.entity.Event;
import com.aegis.aegisbackend.global.common.enums.EventRisk;
import com.aegis.aegisbackend.global.common.enums.EventStatus;
import com.aegis.aegisbackend.global.common.enums.EventType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
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

    @Query("SELECT e FROM Event e JOIN FETCH e.camera ORDER BY e.occurredAt DESC")
    List<Event> findAllWithCamera();

    @Query("SELECT e FROM Event e JOIN FETCH e.camera WHERE e.camera.id IN :cameraIds ORDER BY e.occurredAt DESC")
    List<Event> findByCameraIdInWithCamera(@Param("cameraIds") List<UUID> cameraIds);

    // 페이지네이션 지원
    @Query(value = "SELECT e FROM Event e JOIN FETCH e.camera ORDER BY e.occurredAt DESC",
           countQuery = "SELECT COUNT(e) FROM Event e")
    Page<Event> findAllWithCameraPaged(Pageable pageable);

    @Query(value = "SELECT e FROM Event e JOIN FETCH e.camera WHERE e.camera.id IN :cameraIds ORDER BY e.occurredAt DESC",
           countQuery = "SELECT COUNT(e) FROM Event e WHERE e.camera.id IN :cameraIds")
    Page<Event> findByCameraIdInWithCameraPaged(@Param("cameraIds") List<UUID> cameraIds, Pageable pageable);

    // 필터링 지원 (Admin용)
    @Query(value = "SELECT e FROM Event e JOIN FETCH e.camera WHERE " +
           "(:risks IS NULL OR e.risk IN :risks) AND " +
           "(:types IS NULL OR e.type IN :types) AND " +
           "(:statuses IS NULL OR e.status IN :statuses) AND " +
           "(:cameraIds IS NULL OR e.camera.id IN :cameraIds) AND " +
           "(:startDate IS NULL OR e.occurredAt >= :startDate) AND " +
           "(:endDate IS NULL OR e.occurredAt <= :endDate) " +
           "ORDER BY e.occurredAt DESC",
           countQuery = "SELECT COUNT(e) FROM Event e WHERE " +
           "(:risks IS NULL OR e.risk IN :risks) AND " +
           "(:types IS NULL OR e.type IN :types) AND " +
           "(:statuses IS NULL OR e.status IN :statuses) AND " +
           "(:cameraIds IS NULL OR e.camera.id IN :cameraIds) AND " +
           "(:startDate IS NULL OR e.occurredAt >= :startDate) AND " +
           "(:endDate IS NULL OR e.occurredAt <= :endDate)")
    Page<Event> findAllWithFilters(
            @Param("risks") List<EventRisk> risks,
            @Param("types") List<EventType> types,
            @Param("statuses") List<EventStatus> statuses,
            @Param("cameraIds") List<UUID> cameraIds,
            @Param("startDate") LocalDateTime startDate,
            @Param("endDate") LocalDateTime endDate,
            Pageable pageable);

    // 필터링 지원 (일반 사용자용 - 할당된 카메라만)
    @Query(value = "SELECT e FROM Event e JOIN FETCH e.camera WHERE " +
           "e.camera.id IN :assignedCameraIds AND " +
           "(:risks IS NULL OR e.risk IN :risks) AND " +
           "(:types IS NULL OR e.type IN :types) AND " +
           "(:statuses IS NULL OR e.status IN :statuses) AND " +
           "(:cameraIds IS NULL OR e.camera.id IN :cameraIds) AND " +
           "(:startDate IS NULL OR e.occurredAt >= :startDate) AND " +
           "(:endDate IS NULL OR e.occurredAt <= :endDate) " +
           "ORDER BY e.occurredAt DESC",
           countQuery = "SELECT COUNT(e) FROM Event e WHERE " +
           "e.camera.id IN :assignedCameraIds AND " +
           "(:risks IS NULL OR e.risk IN :risks) AND " +
           "(:types IS NULL OR e.type IN :types) AND " +
           "(:statuses IS NULL OR e.status IN :statuses) AND " +
           "(:cameraIds IS NULL OR e.camera.id IN :cameraIds) AND " +
           "(:startDate IS NULL OR e.occurredAt >= :startDate) AND " +
           "(:endDate IS NULL OR e.occurredAt <= :endDate)")
    Page<Event> findByAssignedCamerasWithFilters(
            @Param("assignedCameraIds") List<UUID> assignedCameraIds,
            @Param("risks") List<EventRisk> risks,
            @Param("types") List<EventType> types,
            @Param("statuses") List<EventStatus> statuses,
            @Param("cameraIds") List<UUID> cameraIds,
            @Param("startDate") LocalDateTime startDate,
            @Param("endDate") LocalDateTime endDate,
            Pageable pageable);

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
