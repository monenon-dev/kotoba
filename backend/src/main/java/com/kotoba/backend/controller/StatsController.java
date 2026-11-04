package com.kotoba.backend.controller;

import java.time.LocalDate;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.kotoba.backend.controller.dto.DailyStatsResponse;
import com.kotoba.backend.controller.dto.StatsSummaryResponse;
import com.kotoba.backend.security.AuthenticatedMember;
import com.kotoba.backend.service.StatsService;

@RestController
@RequestMapping("/api/stats")
public class StatsController {

	private final StatsService statsService;

	public StatsController(StatsService statsService) {
		this.statsService = statsService;
	}

	@GetMapping("/summary")
	public StatsSummaryResponse summary(@AuthenticationPrincipal AuthenticatedMember member) {
		return statsService.summary(member.id());
	}

	// from, to 는 YYYY-MM-DD (기준 시간대의 날짜), 양 끝 포함, 최대 90일
	@GetMapping("/daily")
	public DailyStatsResponse daily(@AuthenticationPrincipal AuthenticatedMember member,
			@RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
			@RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
		return statsService.daily(member.id(), from, to);
	}

}
