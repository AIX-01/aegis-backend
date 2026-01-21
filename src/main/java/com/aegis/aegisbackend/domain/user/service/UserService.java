package com.aegis.aegisbackend.domain.user.service;

import com.aegis.aegisbackend.domain.user.dto.UserDto;
import com.aegis.aegisbackend.domain.camera.entity.Camera;
import com.aegis.aegisbackend.domain.user.entity.User;
import com.aegis.aegisbackend.domain.camera.entity.UserCamera;
import com.aegis.aegisbackend.domain.notification.service.SseEmitterService;
import com.aegis.aegisbackend.global.common.enums.UserRole;
import com.aegis.aegisbackend.global.exception.AegisException;
import com.aegis.aegisbackend.global.exception.ErrorCode;
import com.aegis.aegisbackend.domain.camera.repository.CameraRepository;
import com.aegis.aegisbackend.domain.camera.repository.UserCameraRepository;
import com.aegis.aegisbackend.domain.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final UserCameraRepository userCameraRepository;
    private final CameraRepository cameraRepository;
    private final SseEmitterService sseEmitterService;

    @Transactional(readOnly = true)
    public List<UserDto> getAllUsers() {
        return userRepository.findAllWithCameras().stream()
                .map(this::toUserDto)
                .toList();
    }

    @Transactional(readOnly = true)
    public UserDto getUserById(UUID userId) {
        User user = userRepository.findByIdWithCameras(userId)
                .orElseThrow(() -> new AegisException(ErrorCode.USER_NOT_FOUND_BY_ID));

        return toUserDto(user);
    }

    @Transactional
    public UserDto updateUser(UUID userId, UserDto.UpdateRequest request) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new AegisException(ErrorCode.USER_NOT_FOUND_BY_ID));

        // 이름 업데이트
        if (request.getName() != null) {
            user.setName(request.getName());
        }

        // 역할 업데이트
        if (request.getRole() != null) {
            user.setRole(UserRole.fromValue(request.getRole()));
        }

        // 할당된 카메라 업데이트
        if (request.getAssignedCameras() != null) {
            // 기존 할당 삭제
            userCameraRepository.deleteByUserId(userId);

            // 새로운 카메라 할당
            if (!request.getAssignedCameras().contains("all")) {
                for (String cameraIdStr : request.getAssignedCameras()) {
                    UUID cameraId = UUID.fromString(cameraIdStr);
                    Camera camera = cameraRepository.findById(cameraId)
                            .orElseThrow(() -> new AegisException(ErrorCode.CAMERA_NOT_FOUND));

                    UserCamera userCamera = UserCamera.builder()
                            .user(user)
                            .camera(camera)
                            .build();

                    userCameraRepository.save(userCamera);
                }
            }
        }

        userRepository.save(user);
        log.info("User updated: {}", userId);

        // SSE로 멤버 업데이트 브로드캐스트
        UserDto userDto = toUserDto(user);
        sseEmitterService.broadcastMember(userDto);

        return userDto;
    }

    @Transactional
    public void deleteUser(UUID userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new AegisException(ErrorCode.USER_NOT_FOUND_BY_ID));

        UserDto userDto = toUserDto(user);
        userRepository.deleteById(userId);
        log.info("User deleted: {}", userId);

        // SSE로 멤버 삭제 브로드캐스트
        sseEmitterService.broadcastMember(userDto);
    }

    @Transactional
    public UserDto approveUser(UUID userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new AegisException(ErrorCode.USER_NOT_FOUND_BY_ID));

        user.setApproved(true);
        userRepository.save(user);
        log.info("User approved: {}", userId);

        // SSE로 멤버 승인 브로드캐스트
        UserDto userDto = toUserDto(user);
        sseEmitterService.broadcastMember(userDto);

        return userDto;
    }

    private UserDto toUserDto(User user) {
        List<String> assignedCameras;

        if (user.getRole() == UserRole.ADMIN) {
            assignedCameras = List.of("all");
        } else {
            assignedCameras = userCameraRepository.findCameraIdsByUserId(user.getId())
                    .stream()
                    .map(UUID::toString)
                    .toList();
        }

        return UserDto.builder()
                .id(user.getId().toString())
                .email(user.getEmail())
                .name(user.getName())
                .role(user.getRole().getValue())
                .assignedCameras(assignedCameras)
                .createdAt(user.getCreatedAt().toString())
                .approved(user.getApproved())
                .build();
    }
}

