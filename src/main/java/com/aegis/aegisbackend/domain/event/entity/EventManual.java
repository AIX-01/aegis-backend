package com.aegis.aegisbackend.domain.event.entity;

import com.aegis.aegisbackend.domain.manual.entity.Manual;
import jakarta.persistence.*;
import lombok.*;

import java.util.UUID;

/**
 * 이벤트-매뉴얼 연결 엔티티
 * - 이벤트 분석 시 참조한 매뉴얼 기록
 */
@Entity
@Table(name = "event_manuals", indexes = {
        @Index(name = "idx_event_manuals_event_id", columnList = "event_id"),
        @Index(name = "idx_event_manuals_manual_id", columnList = "manual_id")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class EventManual {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "event_id", nullable = false)
    private Event event;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "manual_id", nullable = false)
    private Manual manual;
}

