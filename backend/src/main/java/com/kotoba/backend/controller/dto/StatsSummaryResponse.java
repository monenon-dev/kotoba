package com.kotoba.backend.controller.dto;

import java.time.LocalDate;

/**
 * @param dueCards 지금 기한이 지난 카드 수 (신규 카드 제외)
 * @param streakDays 연속 학습일 (최근 366일 범위에서 계산, 최대 366)
 */
public record StatsSummaryResponse(LocalDate today, String timeZone, long todayReviews, long dueCards,
		int streakDays, Last7Days last7Days) {

	/** @param accuracy 정답(AGAIN 이 아닌) 비율 0~1. 기간에 복습이 없으면 null */
	public record Last7Days(LocalDate from, LocalDate to, long reviews, long correct, Double accuracy) {
	}

}
