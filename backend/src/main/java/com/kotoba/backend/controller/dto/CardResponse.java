package com.kotoba.backend.controller.dto;

import java.time.Instant;

import com.kotoba.backend.domain.Card;

public record CardResponse(Long id, Long deckId, String text, String reading, String meaning, String example,
		Instant createdAt, Instant updatedAt) {

	// card.getDeck() 은 LAZY 프록시지만 getId() 는 추가 쿼리 없이 FK 값만 읽는다.
	public static CardResponse from(Card card) {
		return new CardResponse(card.getId(), card.getDeck().getId(), card.getText(), card.getReading(),
				card.getMeaning(), card.getExample(), card.getCreatedAt(), card.getUpdatedAt());
	}

}
