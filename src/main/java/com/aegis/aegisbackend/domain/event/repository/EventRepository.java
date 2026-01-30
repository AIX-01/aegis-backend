package com.aegis.aegisbackend.domain.event.repository;

import com.aegis.aegisbackend.domain.event.entity.Event;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Repository
public interface EventRepository extends JpaRepository<Event, UUID> {

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

    @Query("SELECT e.type, COUNT(e) FROM Event e GROUP BY e.type")
    List<Object[]> countByEventType();

    @Query("SELECT FUNCTION('DATE', e.occurredAt) as date, COUNT(e) FROM Event e WHERE e.occurredAt >= :startDate GROUP BY FUNCTION('DATE', e.occurredAt)")
    List<Object[]> countByDateSince(@Param("startDate") LocalDateTime startDate);

    @Query("SELECT FUNCTION('DATE', e.occurredAt) as date, COUNT(e) FROM Event e WHERE e.occurredAt >= :startDate AND (e.type = 'ASSAULT' OR e.type = 'BURGLARY') GROUP BY FUNCTION('DATE', e.occurredAt)")
    List<Object[]> countAlertsByDateSince(@Param("startDate") LocalDateTime startDate);

    @Query("SELECT FUNCTION('DAYOFWEEK', e.occurredAt) as dayOfWeek, COUNT(e), SUM(CASE WHEN e.status = 'ANALYZED' THEN 1 ELSE 0 END) FROM Event e WHERE e.occurredAt >= :startDate GROUP BY FUNCTION('DAYOFWEEK', e.occurredAt)")
    List<Object[]> countByDayOfWeekSince(@Param("startDate") LocalDateTime startDate);
}
