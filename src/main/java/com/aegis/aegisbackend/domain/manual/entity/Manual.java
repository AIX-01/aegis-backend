package com.aegis.aegisbackend.domain.manual.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * 매뉴얼 엔티티
 * - RAG 검색용 대응 매뉴얼
 * - Qdrant 벡터 DB에 임베딩 저장
 */
@Entity
@Table(name = "manuals", indexes = {
        @Index(name = "idx_manuals_enabled", columnList = "enabled"),
        @Index(name = "idx_manuals_name", columnList = "name")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Manual {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    /** 매뉴얼 이름 */
    @Column(nullable = false)
    private String name;

    /** 매뉴얼 내용 (RAG 검색 대상) */
    @Column(nullable = false, columnDefinition = "TEXT")
    private String content;

    /** 활성화 여부 (enabled=true인 것만 RAG 검색 대상) */
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

