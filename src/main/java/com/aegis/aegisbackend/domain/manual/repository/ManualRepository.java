package com.aegis.aegisbackend.domain.manual.repository;

import com.aegis.aegisbackend.domain.manual.entity.Manual;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.UUID;

/**
 * 매뉴얼 레포지토리
 */
@Repository
public interface ManualRepository extends JpaRepository<Manual, UUID> {

    /** 페이지네이션 조회 (정렬: enabled DESC, name ASC) */
    @Query("SELECT m FROM Manual m ORDER BY m.enabled DESC, m.name ASC")
    Page<Manual> findAllPaged(Pageable pageable);
}

