package com.aegis.aegisbackend.domain.camera.repository;

import com.aegis.aegisbackend.domain.camera.entity.Camera;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * 카메라 레포지토리
 */
@Repository
public interface CameraRepository extends JpaRepository<Camera, UUID> {

    /** 스트림 경로명으로 조회 */
    Optional<Camera> findByName(String name);

    boolean existsByName(String name);

    /** 연결 상태별 조회 */
    List<Camera> findByConnected(boolean connected);

    /** 활성화 상태별 조회 */
    List<Camera> findByEnabled(boolean enabled);

    List<Camera> findByConnectedAndEnabled(boolean connected, boolean enabled);

    /** 분석 대상 카메라 조회 (connected=true AND enabled=true AND analysisEnabled=true) */
    List<Camera> findByConnectedAndEnabledAndAnalysisEnabled(boolean connected, boolean enabled, boolean analysisEnabled);

    @Query("SELECT c FROM Camera c WHERE c.id IN :ids")
    List<Camera> findByIdIn(@Param("ids") List<UUID> ids);

    // 페이지네이션 지원
    @Query("SELECT c FROM Camera c ORDER BY c.connected DESC, c.enabled DESC, c.location ASC")
    Page<Camera> findAllPaged(Pageable pageable);

    @Query("SELECT c FROM Camera c WHERE c.id IN :ids ORDER BY c.connected DESC, c.enabled DESC, c.location ASC")
    Page<Camera> findByIdInPaged(@Param("ids") List<UUID> ids, Pageable pageable);

    @Modifying(clearAutomatically = true)
    @Query("UPDATE Camera c SET c.connected = :connected WHERE c.name NOT IN :names")
    int updateConnectedForCamerasNotInNames(@Param("connected") boolean connected, @Param("names") List<String> names);

    @Modifying(clearAutomatically = true)
    @Query("UPDATE Camera c SET c.connected = true WHERE c.name IN :names")
    int updateConnectedTrueForCamerasInNames(@Param("names") List<String> names);

    @Query("SELECT c.name FROM Camera c")
    List<String> findAllCameraNames();
}
