package com.aegis.aegisbackend.domain.camera.repository;

import com.aegis.aegisbackend.domain.camera.entity.UserCamera;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface UserCameraRepository extends JpaRepository<UserCamera, UserCamera.UserCameraId> {

    List<UserCamera> findByUserId(UUID userId);

    List<UserCamera> findByCameraId(UUID cameraId);

    @Modifying
    @Query("DELETE FROM UserCamera uc WHERE uc.user.id = :userId")
    void deleteByUserId(@Param("userId") UUID userId);

    @Modifying
    @Query("DELETE FROM UserCamera uc WHERE uc.camera.id = :cameraId")
    void deleteByCameraId(@Param("cameraId") UUID cameraId);

    @Query("SELECT uc.camera.id FROM UserCamera uc WHERE uc.user.id = :userId")
    List<UUID> findCameraIdsByUserId(@Param("userId") UUID userId);

    boolean existsByUserIdAndCameraId(UUID userId, UUID cameraId);
}

