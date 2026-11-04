package com.kotoba.backend.controller.dto;

import java.time.LocalDate;
import java.util.List;

/**
 * 날짜별 복습 수. 기간 안의 모든 날짜가 포함되며, 기록이 없는 날은 0.
 */
public record DailyStatsResponse(LocalDate from, LocalDate to, String timeZone, List<Day> days) {

	public record Day(LocalDate date, long reviews) {
	}

}
