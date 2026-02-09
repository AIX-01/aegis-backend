package com.aegis.aegisbackend.domain.event.repository;

import com.aegis.aegisbackend.domain.event.entity.Event;
import com.aegis.aegisbackend.global.common.enums.EventRisk;
import com.aegis.aegisbackend.global.common.enums.EventStatus;
import com.aegis.aegisbackend.global.common.enums.EventType;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * 이벤트 동적 쿼리 Specification
 */
public class EventSpecification {

    public static Specification<Event> withFilters(
            List<EventRisk> risks,
            List<EventType> types,
            List<EventStatus> statuses,
            List<UUID> cameraIds,
            LocalDateTime startDate,
            LocalDateTime endDate,
            List<UUID> assignedCameraIds) {

        return (root, query, criteriaBuilder) -> {
            List<Predicate> predicates = new ArrayList<>();

            // Fetch join for camera (only for non-count queries)
            if (query.getResultType() != Long.class && query.getResultType() != long.class) {
                root.fetch("camera");
            }

            // 할당된 카메라 필터 (일반 사용자용)
            if (assignedCameraIds != null && !assignedCameraIds.isEmpty()) {
                predicates.add(root.get("camera").get("id").in(assignedCameraIds));
            }

            // 위험도 필터
            if (risks != null && !risks.isEmpty()) {
                predicates.add(root.get("risk").in(risks));
            }

            // 이벤트 타입 필터
            if (types != null && !types.isEmpty()) {
                predicates.add(root.get("type").in(types));
            }

            // 상태 필터
            if (statuses != null && !statuses.isEmpty()) {
                predicates.add(root.get("status").in(statuses));
            }

            // 카메라 ID 필터
            if (cameraIds != null && !cameraIds.isEmpty()) {
                predicates.add(root.get("camera").get("id").in(cameraIds));
            }

            // 시작 날짜 필터
            if (startDate != null) {
                predicates.add(criteriaBuilder.greaterThanOrEqualTo(root.get("occurredAt"), startDate));
            }

            // 종료 날짜 필터
            if (endDate != null) {
                predicates.add(criteriaBuilder.lessThanOrEqualTo(root.get("occurredAt"), endDate));
            }

            // 정렬: occurredAt DESC
            query.orderBy(criteriaBuilder.desc(root.get("occurredAt")));

            return criteriaBuilder.and(predicates.toArray(new Predicate[0]));
        };
    }
}
