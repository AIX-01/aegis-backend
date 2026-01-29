package com.aegis.aegisbackend.domain.event.repository;

import com.aegis.aegisbackend.domain.event.entity.Event;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Repository
public interface EventRepository extends JpaRepository<Event, UUID> {

    @Query("SELECT e FROM Event e JOIN FETCH e.camera ORDER BY e.timestamp DESC")
    List<Event> findAllWithCamera();

    @Query("SELECT e FROM Event e JOIN FETCH e.camera WHERE e.camera.id IN :cameraIds ORDER BY e.timestamp DESC")
    List<Event> findByCameraIdInWithCamera(@Param("cameraIds") List<UUID> cameraIds);


    @Query("SELECT e.type, COUNT(e) FROM Event e GROUP BY e.type")
    List<Object[]> countByEventType();

    @Query("SELECT FUNCTION('DATE', e.timestamp) as date, COUNT(e) FROM Event e WHERE e.timestamp >= :startDate GROUP BY FUNCTION('DATE', e.timestamp)")
    List<Object[]> countByDateSince(@Param("startDate") LocalDateTime startDate);

    @Query("SELECT FUNCTION('DATE', e.timestamp) as date, COUNT(e) FROM Event e WHERE e.timestamp >= :startDate AND (e.type = 'ASSAULT' OR e.type = 'BURGLARY') GROUP BY FUNCTION('DATE', e.timestamp)")
    List<Object[]> countAlertsByDateSince(@Param("startDate") LocalDateTime startDate);

    @Query("SELECT FUNCTION('DAYOFWEEK', e.timestamp) as dayOfWeek, COUNT(e), SUM(CASE WHEN e.status = 'RESOLVED' THEN 1 ELSE 0 END) FROM Event e WHERE e.timestamp >= :startDate GROUP BY FUNCTION('DAYOFWEEK', e.timestamp)")
    List<Object[]> countByDayOfWeekSince(@Param("startDate") LocalDateTime startDate);
}

