package com.aegis.aegisbackend.domain.camera.entity;

import com.aegis.aegisbackend.domain.user.entity.User;
import jakarta.persistence.*;
import lombok.*;

import java.io.Serializable;
import java.util.UUID;

@Entity
@Table(name = "user_cameras")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@IdClass(UserCamera.UserCameraId.class)
public class UserCamera {

    @Id
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Id
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "camera_id", nullable = false)
    private Camera camera;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class UserCameraId implements Serializable {
        private static final long serialVersionUID = 1L;
        private UUID user;
        private UUID camera;
    }
}

