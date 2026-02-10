package com.aegis.aegisbackend.domain.event.entity;

import com.aegis.aegisbackend.domain.action.entity.Action;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

/**
 * 이벤트-액션 실행 기록 엔티티
 * - Agent가 Tool 실행 후 결과 기록
 */
@Entity
@Table(name = "event_actions", indexes = {
        @Index(name = "idx_event_actions_event_id", columnList = "event_id"),
        @Index(name = "idx_event_actions_action_id", columnList = "action_id")
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

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "action_id", nullable = false)
    private Action action;

    /** 실행에 사용된 파라미터 */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "input_params", columnDefinition = "jsonb")
    private Map<String, Object> inputParams;

    /** 실행 결과 (성공 시 결과, 실패 시 에러 메시지) */
    @Column(name = "output_result", columnDefinition = "TEXT")
    private String outputResult;

    /** 실행 성공 여부 */
    @Column(nullable = false)
    private Boolean success;

    /** 실행 시작 시점 (Agent에서 전달) */
    @Column(name = "executed_at", nullable = false)
    private LocalDateTime executedAt;
}
