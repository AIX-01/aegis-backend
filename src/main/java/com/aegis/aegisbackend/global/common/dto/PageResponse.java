package com.aegis.aegisbackend.global.common.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.Collections;
import java.util.List;
import java.util.function.Function;

/**
 * 페이지네이션 응답 DTO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PageResponse<T> {
    private List<T> content;
    private int page;
    private int size;
    private long totalElements;
    private int totalPages;
    private boolean first;
    private boolean last;

    public static <T, E> PageResponse<T> from(Page<E> page, Function<E, T> mapper) {
        return PageResponse.<T>builder()
                .content(page.getContent().stream().map(mapper).toList())
                .page(page.getNumber())
                .size(page.getSize())
                .totalElements(page.getTotalElements())
                .totalPages(page.getTotalPages())
                .first(page.isFirst())
                .last(page.isLast())
                .build();
    }

    public static <T> PageResponse<T> empty(Pageable pageable) {
        return PageResponse.<T>builder()
                .content(Collections.emptyList())
                .page(pageable.getPageNumber())
                .size(pageable.getPageSize())
                .totalElements(0)
                .totalPages(0)
                .first(true)
                .last(true)
                .build();
    }
}
