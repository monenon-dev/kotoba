package com.kotoba.backend.controller.dto;

import java.util.List;
import java.util.function.Function;

import org.springframework.data.domain.Page;

/**
 * 목록 응답의 공통 형식. Spring 의 Page 를 그대로 직렬화하지 않고 필요한 값만 고정된 이름으로 내보낸다.
 */
public record PageResponse<T>(List<T> content, int page, int size, long totalElements, int totalPages,
		boolean hasNext) {

	public static <E, T> PageResponse<T> of(Page<E> page, Function<E, T> mapper) {
		return new PageResponse<>(page.getContent().stream().map(mapper).toList(), page.getNumber(), page.getSize(),
				page.getTotalElements(), page.getTotalPages(), page.hasNext());
	}

}
