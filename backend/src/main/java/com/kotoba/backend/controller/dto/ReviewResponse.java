package com.kotoba.backend.controller.dto;

import java.time.Instant;

import com.kotoba.backend.domain.ReviewState;
import com.kotoba.backend.srs.Rating;

/**
 * 복습 결과. previousIntervalDays 는 신규 카드면 null.
 */
public record ReviewResponse(Long cardId, Rating rating, Instant reviewedAt, Integer previousIntervalDays,
		int intervalDays, double easeFactor, int repetitions, int lapses, Instant dueAt) {

	public static ReviewResponse of(Long cardId, Rating rating, Instant reviewedAt, Integer previousIntervalDays,
			ReviewState state) {
		return new ReviewResponse(cardId, rating, reviewedAt, previousIntervalDays, state.getIntervalDays(),
				state.schedule().easeFactor(), state.getRepetitions(), state.getLapses(), state.getDueAt());
	}

}
