package com.aegis.aegisbackend.service;

import com.aegis.aegisbackend.dto.SettingsDto.*;
import com.aegis.aegisbackend.entity.EmergencyContact;
import com.aegis.aegisbackend.entity.enums.ContactType;
import com.aegis.aegisbackend.repository.EmergencyContactRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class EmergencyContactService {

    private final EmergencyContactRepository emergencyContactRepository;

    @Transactional(readOnly = true)
    public List<EmergencyContactResponse> getAllContacts() {
        List<EmergencyContact> contacts = emergencyContactRepository.findAll();
        List<EmergencyContactResponse> responses = new ArrayList<>();

        for (EmergencyContact contact : contacts) {
            responses.add(toResponse(contact));
        }

        return responses;
    }

    @Transactional
    public void updateContacts(EmergencyContactUpdateRequest request) {
        // Primary 연락처 업데이트
        if (request.getPrimary() != null) {
            updateOrCreateContact(ContactType.PRIMARY, request.getPrimary());
        }

        // Secondary 연락처 업데이트
        if (request.getSecondary() != null) {
            updateOrCreateContact(ContactType.SECONDARY, request.getSecondary());
        }

        log.info("Emergency contacts updated");
    }

    private void updateOrCreateContact(ContactType type, ContactInfo info) {
        EmergencyContact contact = emergencyContactRepository.findByType(type)
                .orElse(EmergencyContact.builder()
                        .type(type)
                        .build());

        contact.setPhone(info.getPhone());
        contact.setEmail(info.getEmail());
        emergencyContactRepository.save(contact);
    }

    private EmergencyContactResponse toResponse(EmergencyContact contact) {
        return EmergencyContactResponse.builder()
                .id(contact.getId().toString())
                .type(contact.getType().getValue())
                .phone(contact.getPhone())
                .email(contact.getEmail())
                .build();
    }
}
