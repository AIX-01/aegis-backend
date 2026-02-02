package com.aegis.aegisbackend.domain.event.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * 이벤트 액션 로그 엔티티
 */
@Entity
@Table(name = "event_actions", indexes = {
        @Index(name = "idx_event_actions_event_id", columnList = "event_id")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class EventAction {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "event_id", nullable = false)
    private Event event;

    /** 액션 로그 */
    @Column(columnDefinition = "TEXT", nullable = false)
    private String log;

    /** 발동 시각 */
    @Column(name = "triggered_at", nullable = false)
    private LocalDateTime triggeredAt;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;
}
