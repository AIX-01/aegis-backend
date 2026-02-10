package com.aegis.aegisbackend.domain.event.repository;

import com.aegis.aegisbackend.domain.event.entity.EventManual;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface EventManualRepository extends JpaRepository<EventManual, UUID> {

    List<EventManual> findByEventId(UUID eventId);

    void deleteByEventId(UUID eventId);
}

