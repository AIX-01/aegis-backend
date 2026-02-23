package com.aegis.aegisbackend.domain.event.repository;

import com.aegis.aegisbackend.domain.event.entity.EventAction;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface EventActionRepository extends JpaRepository<EventAction, UUID> {

    List<EventAction> findByEventIdOrderByCreatedAtAsc(UUID eventId);

    void deleteByEventId(UUID eventId);
}
