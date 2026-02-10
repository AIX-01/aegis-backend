package com.aegis.aegisbackend.domain.action.repository;

import com.aegis.aegisbackend.domain.action.entity.Action;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

/**
 * 액션 레포지토리
 */
@Repository
public interface ActionRepository extends JpaRepository<Action, UUID> {

    /** enabled=true인 액션 목록 조회 (Redis 동기화용) */
    List<Action> findByEnabledTrue();

    /** 페이지네이션 조회 (정렬: enabled DESC, name ASC) */
    @Query("SELECT a FROM Action a ORDER BY a.enabled DESC, a.name ASC")
    Page<Action> findAllPaged(Pageable pageable);
}

