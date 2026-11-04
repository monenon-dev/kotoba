package com.kotoba.backend.controller.dto;

import java.time.Instant;

import com.kotoba.backend.domain.Deck;

public record DeckResponse(Long id, String name, String description, long cardCount, Instant createdAt,
		Instant updatedAt) {

	public static DeckResponse of(Deck deck, long cardCount) {
		return new DeckResponse(deck.getId(), deck.getName(), deck.getDescription(), cardCount, deck.getCreatedAt(),
				deck.getUpdatedAt());
	}

}
