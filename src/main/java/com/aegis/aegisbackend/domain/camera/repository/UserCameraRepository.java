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
public interface UserCameraRepository extends JpaRepository<UserCamera, UUID> {

    @Modifying(clearAutomatically = true)
    @Query("DELETE FROM UserCamera uc WHERE uc.user.id = :userId")
    void deleteByUserId(@Param("userId") UUID userId);

    @Query("SELECT uc.camera.id FROM UserCamera uc WHERE uc.user.id = :userId")
    List<UUID> findCameraIdsByUserId(@Param("userId") UUID userId);
}

