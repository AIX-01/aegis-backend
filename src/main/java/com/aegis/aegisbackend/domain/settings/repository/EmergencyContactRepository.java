package com.aegis.aegisbackend.domain.settings.repository;

import com.aegis.aegisbackend.domain.settings.entity.EmergencyContact;
import com.aegis.aegisbackend.global.common.enums.ContactType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface EmergencyContactRepository extends JpaRepository<EmergencyContact, UUID> {

    Optional<EmergencyContact> findByType(ContactType type);

    boolean existsByType(ContactType type);
}

