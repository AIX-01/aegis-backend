package com.aegis.aegisbackend.domain.user.repository;

import com.aegis.aegisbackend.domain.user.entity.User;
import com.aegis.aegisbackend.global.common.enums.UserRole;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface UserRepository extends JpaRepository<User, UUID> {

    Optional<User> findByEmail(String email);

    boolean existsByEmail(String email);

    List<User> findByRole(UserRole role);

    @Query("SELECT u FROM User u LEFT JOIN FETCH u.userCameras uc LEFT JOIN FETCH uc.camera WHERE u.id = :id")
    Optional<User> findByIdWithCameras(@Param("id") UUID id);

    @Query("SELECT u FROM User u JOIN u.userCameras uc WHERE uc.camera.id = :cameraId")
    List<User> findUsersByCameraId(@Param("cameraId") UUID cameraId);


    // 승인된 사용자 페이지네이션 (정렬은 Pageable에서 처리)
    @Query(value = "SELECT DISTINCT u FROM User u LEFT JOIN FETCH u.userCameras uc LEFT JOIN FETCH uc.camera WHERE u.approved = true",
           countQuery = "SELECT COUNT(u) FROM User u WHERE u.approved = true")
    Page<User> findApprovedUsersPaged(Pageable pageable);

    // 미승인 사용자 페이지네이션 (정렬은 Pageable에서 처리)
    @Query(value = "SELECT DISTINCT u FROM User u LEFT JOIN FETCH u.userCameras uc LEFT JOIN FETCH uc.camera WHERE u.approved = false",
           countQuery = "SELECT COUNT(u) FROM User u WHERE u.approved = false")
    Page<User> findPendingUsersPaged(Pageable pageable);

    // 미승인 사용자 수
    @Query("SELECT COUNT(u) FROM User u WHERE u.approved = false")
    long countPendingUsers();
}

