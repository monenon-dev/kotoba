package com.kotoba.backend.controller.dto;

import com.kotoba.backend.domain.Deck;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 덱 생성/수정 요청. PUT 은 전체 교체이므로 description 을 생략하면 null 로 바뀐다.
 */
public record DeckRequest(
		@NotBlank(message = "덱 이름을 입력해 주세요.")
		@Size(max = Deck.NAME_MAX_LENGTH, message = "덱 이름은 {max}자 이하여야 합니다.")
		String name,

		@Size(max = Deck.DESCRIPTION_MAX_LENGTH, message = "설명은 {max}자 이하여야 합니다.")
		String description) {
}
