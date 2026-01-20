package com.aegis.aegisbackend.domain.camera.repository;

import com.aegis.aegisbackend.domain.camera.entity.Camera;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface CameraRepository extends JpaRepository<Camera, UUID> {

    Optional<Camera> findByName(String name);

    boolean existsByName(String name);

    List<Camera> findByConnected(boolean connected);

    List<Camera> findByActive(boolean active);

    List<Camera> findByConnectedAndActive(boolean connected, boolean active);

    @Query("SELECT c FROM Camera c WHERE c.id IN :ids")
    List<Camera> findByIdIn(@Param("ids") List<UUID> ids);

    @Modifying
    @Query("UPDATE Camera c SET c.connected = :connected WHERE c.name NOT IN :names")
    int updateConnectedForCamerasNotInNames(@Param("connected") boolean connected, @Param("names") List<String> names);

    @Modifying
    @Query("UPDATE Camera c SET c.connected = true WHERE c.name IN :names")
    int updateConnectedTrueForCamerasInNames(@Param("names") List<String> names);

    @Query("SELECT c.name FROM Camera c")
    List<String> findAllCameraNames();
}

