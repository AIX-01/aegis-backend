package com.aegis.aegisbackend.domain.event.entity;

import com.aegis.aegisbackend.domain.camera.entity.Camera;
import com.aegis.aegisbackend.domain.notification.entity.Notification;
import com.aegis.aegisbackend.global.common.enums.EventRisk;
import com.aegis.aegisbackend.global.common.enums.EventStatus;
import com.aegis.aegisbackend.global.common.enums.EventType;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * 이벤트 엔티티
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

    /** 위험 수준 */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private EventRisk risk;

    /** 이벤트 유형 */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private EventType type;

    @Column(name = "occurred_at", nullable = false)
    private LocalDateTime occurredAt;

    @Column(name = "clip_url", columnDefinition = "TEXT")
    private String clipUrl;

    /** AI 분석 요약 */
    @Column(columnDefinition = "TEXT")
    private String summary;

    /** 상세 보고서 */
    @Column(columnDefinition = "TEXT")
    private String report;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private EventStatus status = EventStatus.PROCESSING;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    /** 액션 목록 (생성순 정렬) */
    @OneToMany(mappedBy = "event", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("createdAt ASC")
    @Builder.Default
    private List<EventAction> actions = new ArrayList<>();

    @OneToMany(mappedBy = "event", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private Set<Notification> notifications = new HashSet<>();

    /** 액션 추가 */
    public void addAction(EventAction action) {
        actions.add(action);
        action.setEvent(this);
    }

    /** 액션 일괄 추가 */
    public void addActions(List<EventAction> newActions) {
        for (EventAction action : newActions) {
            addAction(action);
        }
    }
}
