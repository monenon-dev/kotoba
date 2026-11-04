package com.kotoba.backend.controller.dto;

import com.kotoba.backend.domain.Card;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 카드 생성/수정 요청. PUT 은 전체 교체이므로 reading, example 을 생략하면 null 로 바뀐다.
 */
public record CardRequest(
		@NotBlank(message = "표현을 입력해 주세요.")
		@Size(max = Card.TEXT_MAX_LENGTH, message = "표현은 {max}자 이하여야 합니다.")
		String text,

		@Size(max = Card.READING_MAX_LENGTH, message = "읽기는 {max}자 이하여야 합니다.")
		String reading,

		@NotBlank(message = "뜻을 입력해 주세요.")
		@Size(max = Card.MEANING_MAX_LENGTH, message = "뜻은 {max}자 이하여야 합니다.")
		String meaning,

		@Size(max = Card.EXAMPLE_MAX_LENGTH, message = "예문은 {max}자 이하여야 합니다.")
		String example) {
}
