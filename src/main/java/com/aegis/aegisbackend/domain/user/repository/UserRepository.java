package com.aegis.aegisbackend.domain.user.repository;

import com.aegis.aegisbackend.domain.user.entity.User;
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


    @Query("SELECT u FROM User u LEFT JOIN FETCH u.userCameras uc LEFT JOIN FETCH uc.camera WHERE u.id = :id")
    Optional<User> findByIdWithCameras(@Param("id") UUID id);

    @Query("SELECT u FROM User u LEFT JOIN FETCH u.userCameras uc LEFT JOIN FETCH uc.camera WHERE u.email = :email")
    Optional<User> findByEmailWithCameras(@Param("email") String email);

    @Query("SELECT u FROM User u JOIN u.userCameras uc WHERE uc.camera.id = :cameraId")
    List<User> findUsersByCameraId(@Param("cameraId") UUID cameraId);

    @Query("SELECT DISTINCT u FROM User u LEFT JOIN FETCH u.userCameras uc LEFT JOIN FETCH uc.camera")
    List<User> findAllWithCameras();
}

