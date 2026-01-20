package com.aegis.aegisbackend.domain.settings.controller;

import com.aegis.aegisbackend.domain.settings.dto.SettingsDto.*;
import com.aegis.aegisbackend.domain.settings.service.EmergencyContactService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 설정 API
 * - 비상연락처 관리
 */
@RestController
@RequestMapping("/api/settings")
@RequiredArgsConstructor
public class SettingsController {

    private final EmergencyContactService emergencyContactService;

    @GetMapping("/emergency-contacts")
    public ResponseEntity<List<EmergencyContactResponse>> getEmergencyContacts() {
        List<EmergencyContactResponse> contacts = emergencyContactService.getAllContacts();
        return ResponseEntity.ok(contacts);
    }

    @PutMapping("/emergency-contacts")
    public ResponseEntity<Map<String, Object>> updateEmergencyContacts(
            @RequestBody EmergencyContactUpdateRequest request) {
        emergencyContactService.updateContacts(request);
        return ResponseEntity.ok(Map.of(
                "success", true,
                "message", "비상 연락처가 수정되었습니다."
        ));
    }
}
