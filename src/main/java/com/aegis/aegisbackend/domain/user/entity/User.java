package com.aegis.aegisbackend.domain.user.entity;

import com.aegis.aegisbackend.domain.camera.entity.UserCamera;
import com.aegis.aegisbackend.domain.notification.entity.Notification;
import com.aegis.aegisbackend.global.common.enums.UserRole;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * 사용자 엔티티
 * - 관리자(ADMIN) / 일반 사용자(USER) 구분
 * - 회원가입 후 관리자 승인 필요
 */
@Entity
@Table(name = "users", indexes = {
        @Index(name = "idx_users_email", columnList = "email"),
        @Index(name = "idx_users_approved", columnList = "approved")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    /** 로그인 이메일 (고유) */
    @Column(nullable = false, unique = true, length = 255)
    private String email;

    /** 암호화된 비밀번호 */
    @Column(nullable = false, length = 255)
    private String password;

    /** 사용자 이름 */
    @Column(nullable = false, length = 100)
    private String name;

    /** 사용자 권한 (ADMIN/USER) */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    @Builder.Default
    private UserRole role = UserRole.USER;

    /** 관리자 승인 여부 */
    @Column(nullable = false)
    @Builder.Default
    private Boolean approved = false;

    /** 탈퇴 여부 (소프트 딜리트) */
    @Column(nullable = false)
    @Builder.Default
    private Boolean deleted = false;

    /** 탈퇴 일시 */
    private LocalDateTime deletedAt;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(nullable = false)
    private LocalDateTime updatedAt;

    @OneToMany(mappedBy = "user", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private Set<UserCamera> userCameras = new HashSet<>();

    @OneToMany(mappedBy = "user", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private Set<Notification> notifications = new HashSet<>();
}

