package com.aegis.aegisbackend.domain.camera.entity;

import com.aegis.aegisbackend.domain.event.entity.Event;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * 카메라 엔티티
 * - MediaMTX에서 동기화된 CCTV 카메라 정보
 */
@Entity
@Table(name = "cameras", indexes = {
        @Index(name = "idx_cameras_connected", columnList = "connected"),
        @Index(name = "idx_cameras_active", columnList = "active")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Camera {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    /** MediaMTX 스트림 경로명 (예: cam1) */
    @Column(nullable = false, length = 50)
    private String name;

    /** 연결 상태 (MediaMTX에서 스트림 수신 여부) */
    @Column(nullable = false)
    @Builder.Default
    private Boolean connected = false;

    /** 사용자 지정 별칭 */
    @Column(nullable = false, length = 100)
    private String alias;

    /** 모니터링 활성화 여부 */
    @Column(nullable = false)
    @Builder.Default
    private Boolean active = false;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(nullable = false)
    private LocalDateTime updatedAt;

    @OneToMany(mappedBy = "camera", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private Set<UserCamera> userCameras = new HashSet<>();

    @OneToMany(mappedBy = "camera", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private Set<Event> events = new HashSet<>();

    @PrePersist
    public void prePersist() {
        if (this.alias == null || this.alias.isEmpty()) {
            this.alias = this.name;
        }
    }
}

