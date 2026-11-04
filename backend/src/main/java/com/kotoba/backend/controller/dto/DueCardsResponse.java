package com.kotoba.backend.controller.dto;

import java.time.Instant;
import java.util.List;

import com.kotoba.backend.domain.Card;
import com.kotoba.backend.domain.ReviewState;

/**
 * 지금 복습할 카드 목록. 기한이 지난 카드가 먼저, 이어서 신규 카드.
 *
 * @param remainingNewToday 이 목록을 받기 전 기준으로 오늘 더 시작할 수 있는 신규 카드 수
 */
public record DueCardsResponse(Long deckId, int dueCount, int newCount, int remainingNewToday,
		List<DueCard> cards) {

	/** 신규 카드면 isNew = true 이고 dueAt, intervalDays, repetitions 는 null. */
	public record DueCard(Long cardId, String text, String reading, String meaning, String example, boolean isNew,
			Instant dueAt, Integer intervalDays, Integer repetitions) {

		public static DueCard due(ReviewState state) {
			Card card = state.getCard();
			return new DueCard(card.getId(), card.getText(), card.getReading(), card.getMeaning(), card.getExample(),
					false, state.getDueAt(), state.getIntervalDays(), state.getRepetitions());
		}

		public static DueCard fresh(Card card) {
			return new DueCard(card.getId(), card.getText(), card.getReading(), card.getMeaning(), card.getExample(),
					true, null, null, null);
		}

	}

}
