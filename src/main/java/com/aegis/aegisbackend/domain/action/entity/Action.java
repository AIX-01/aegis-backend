package com.aegis.aegisbackend.domain.action.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

/**
 * 액션(Tool) 엔티티
 * - LLM Agent가 사용하는 동적 Tool 정의
 * - Redis를 통해 Python Agent와 동기화
 */
@Entity
@Table(name = "actions", indexes = {
        @Index(name = "idx_actions_enabled", columnList = "enabled"),
        @Index(name = "idx_actions_name", columnList = "name")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Action {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    /** Tool 이름 */
    @Column(nullable = false)
    private String name;

    /** Tool 설명 (LLM이 참고) */
    @Column(nullable = false, columnDefinition = "TEXT")
    private String description;

    /** 파라미터 정의 (JSON) */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private Map<String, Object> parameters;

    /** 실행할 Python 코드 */
    @Column(nullable = false, columnDefinition = "TEXT")
    private String code;

    /** 활성화 여부 (enabled=true인 것만 Agent가 사용) */
    @Column(nullable = false)
    @Builder.Default
    private Boolean enabled = false;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(nullable = false)
    private LocalDateTime updatedAt;
}

