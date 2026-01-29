package com.aegis.aegisbackend.domain.event.entity;

import com.aegis.aegisbackend.domain.camera.entity.Camera;
import com.aegis.aegisbackend.domain.notification.entity.Notification;
import com.aegis.aegisbackend.global.common.enums.EventRisk;
import com.aegis.aegisbackend.global.common.enums.EventStatus;
import com.aegis.aegisbackend.global.common.enums.EventType;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * 이벤트 엔티티
 * - Agent 분석으로 감지된 위험/이상 상황 기록
 */
@Entity
@Table(name = "events", indexes = {
        @Index(name = "idx_events_camera_id", columnList = "camera_id"),
        @Index(name = "idx_events_risk", columnList = "risk"),
        @Index(name = "idx_events_type", columnList = "type"),
        @Index(name = "idx_events_status", columnList = "status"),
        @Index(name = "idx_events_occurred_at", columnList = "occurred_at")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Event {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "camera_id", nullable = false)
    private Camera camera;

    /** 위험 수준 (1차 분류) */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private EventRisk risk;

    /** 이벤트 유형 (2차 분류) */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private EventType type;

    @Column(name = "occurred_at", nullable = false)
    private LocalDateTime occurredAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private EventStatus status = EventStatus.PROCESSING;

    @Column(columnDefinition = "TEXT")
    private String clipUrl;

    @Column(columnDefinition = "TEXT")
    private String summary;

    @Column(length = 10)
    private String riskScore;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private List<Map<String, Object>> actions;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private List<Map<String, Object>> ragReferences;

    @Column(columnDefinition = "TEXT")
    private String report;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(nullable = false)
    private LocalDateTime updatedAt;

    @OneToMany(mappedBy = "event", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private Set<Notification> notifications = new HashSet<>();
}
