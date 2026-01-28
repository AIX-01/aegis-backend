package com.aegis.aegisbackend.domain.camera.entity;

import com.aegis.aegisbackend.domain.user.entity.User;
import jakarta.persistence.*;
import lombok.*;

import java.util.UUID;

/**
 * 사용자-카메라 매핑 엔티티
 * - 사용자에게 할당된 카메라 관리
 * - 동일 사용자-카메라 조합은 유니크 제약조건으로 보장
 */
@Entity
@Table(name = "user_cameras",
    uniqueConstraints = @UniqueConstraint(
        name = "uk_user_cameras_user_camera",
        columnNames = {"user_id", "camera_id"}
    ),
    indexes = {
        @Index(name = "idx_user_cameras_user_id", columnList = "user_id"),
        @Index(name = "idx_user_cameras_camera_id", columnList = "camera_id")
    }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UserCamera {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "camera_id", nullable = false)
    private Camera camera;
}

