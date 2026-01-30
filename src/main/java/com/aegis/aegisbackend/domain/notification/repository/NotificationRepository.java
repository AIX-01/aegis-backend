package com.aegis.aegisbackend.domain.notification.repository;

import com.aegis.aegisbackend.domain.notification.entity.Notification;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface NotificationRepository extends JpaRepository<Notification, UUID> {

    List<Notification> findByUserIdOrderByCreatedAtDesc(UUID userId);


    @Modifying(clearAutomatically = true)
    @Query("DELETE FROM Notification n WHERE n.user.id = :userId")
    void deleteAllByUserId(@Param("userId") UUID userId);

    @Modifying(clearAutomatically = true)
    @Query("DELETE FROM Notification n WHERE n.event.id = :eventId")
    void deleteByEventId(@Param("eventId") UUID eventId);
}

