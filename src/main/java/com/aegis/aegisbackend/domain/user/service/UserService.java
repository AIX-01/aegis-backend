package com.aegis.aegisbackend.domain.user.service;

import com.aegis.aegisbackend.domain.user.dto.UserDto;
import com.aegis.aegisbackend.domain.camera.entity.Camera;
import com.aegis.aegisbackend.domain.user.entity.User;
import com.aegis.aegisbackend.domain.camera.entity.UserCamera;
import com.aegis.aegisbackend.domain.notification.service.SseEmitterService;
import com.aegis.aegisbackend.global.common.dto.PageResponse;
import com.aegis.aegisbackend.global.common.enums.UserRole;
import com.aegis.aegisbackend.global.exception.BusinessException;
import com.aegis.aegisbackend.global.exception.ErrorCode;
import com.aegis.aegisbackend.domain.camera.repository.CameraRepository;
import com.aegis.aegisbackend.domain.camera.repository.UserCameraRepository;
import com.aegis.aegisbackend.domain.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
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

    private static final int DEFAULT_PAGE_SIZE = 20;


    /**
     * 승인된 사용자 목록 조회 (페이지네이션, 최신 가입순 정렬)
     */
    @Transactional(readOnly = true)
    public PageResponse<UserDto> getApprovedUsersPaged(int page, int size) {
        Pageable pageable = PageRequest.of(page, size > 0 ? size : DEFAULT_PAGE_SIZE);
        Page<User> userPage = userRepository.findApprovedUsersPaged(pageable);
        return PageResponse.from(userPage, this::toUserDto);
    }

    /**
     * 미승인 사용자 목록 조회 (페이지네이션, 최신 가입순 정렬)
     */
    @Transactional(readOnly = true)
    public PageResponse<UserDto> getPendingUsersPaged(int page, int size) {
        Pageable pageable = PageRequest.of(page, size > 0 ? size : DEFAULT_PAGE_SIZE);
        Page<User> userPage = userRepository.findPendingUsersPaged(pageable);
        return PageResponse.from(userPage, this::toUserDto);
    }

    /**
     * 미승인 사용자 수 조회
     */
    @Transactional(readOnly = true)
    public long countPendingUsers() {
        return userRepository.countPendingUsers();
    }


    @Transactional(readOnly = true)
    public UserDto getUserById(UUID userId) {
        User user = userRepository.findByIdWithCameras(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND_BY_ID));

        return toUserDto(user);
    }

    @Transactional
    public UserDto updateUser(UUID userId, UserDto.UpdateRequest request) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND_BY_ID));

        // 이름 업데이트
        if (request.getName() != null) {
            user.setName(request.getName());
        }

        // 역할 업데이트
        if (request.getRole() != null) {
            user.setRole(UserRole.fromValue(request.getRole()));
        }

        // 할당된 카메라 업데이트 (어드민은 카메라 권한 수정 불가 - 항상 전체 접근)
        if (request.getAssignedCameras() != null && user.getRole() != UserRole.ADMIN) {
            // 기존 할당 삭제
            userCameraRepository.deleteByUserId(userId);

            // 새로운 카메라 할당
            for (String cameraIdStr : request.getAssignedCameras()) {
                UUID cameraId = UUID.fromString(cameraIdStr);
                Camera camera = cameraRepository.findById(cameraId)
                        .orElseThrow(() -> new BusinessException(ErrorCode.CAMERA_NOT_FOUND));

                UserCamera userCamera = UserCamera.builder()
                        .user(user)
                        .camera(camera)
                        .build();

                userCameraRepository.save(userCamera);
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
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND_BY_ID));

        UserDto userDto = toUserDto(user);
        userRepository.deleteById(userId);
        log.info("User deleted: {}", userId);

        // SSE로 멤버 삭제 브로드캐스트
        sseEmitterService.broadcastMember(userDto);
    }

    @Transactional
    public UserDto approveUser(UUID userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND_BY_ID));

        user.setApproved(true);
        userRepository.save(user);
        log.info("User approved: {}", userId);

        // SSE로 멤버 승인 브로드캐스트
        UserDto userDto = toUserDto(user);
        sseEmitterService.broadcastMember(userDto);

        return userDto;
    }

    /** User 엔티티를 UserDto로 변환 */
    public UserDto toUserDto(User user) {
        // 어드민은 전체 카메라 접근 권한
        List<String> assignedCameras = user.getRole() == UserRole.ADMIN
                ? cameraRepository.findAll().stream()
                        .map(camera -> camera.getId().toString())
                        .toList()
                : userCameraRepository.findCameraIdsByUserId(user.getId())
                        .stream()
                        .map(UUID::toString)
                        .toList();

        return UserDto.from(user, assignedCameras);
    }
}

