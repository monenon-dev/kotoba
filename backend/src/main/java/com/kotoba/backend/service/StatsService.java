package com.kotoba.backend.service;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.kotoba.backend.config.StudyProperties;
import com.kotoba.backend.controller.dto.DailyStatsResponse;
import com.kotoba.backend.controller.dto.StatsSummaryResponse;
import com.kotoba.backend.exception.BusinessException;
import com.kotoba.backend.repository.ReviewLogRepository;
import com.kotoba.backend.repository.ReviewLogRepository.DailyReviewCount;
import com.kotoba.backend.repository.ReviewLogRepository.ReviewCounts;
import com.kotoba.backend.repository.ReviewStateRepository;
import com.kotoba.backend.srs.Rating;

/**
 * 통계는 모두 DB 집계 쿼리로 계산하고 로그 엔티티를 메모리에 올리지 않는다.
 * "오늘" 같은 날짜 경계는 기준 시간대(app.study.zone)로 Java 에서 계산해 파라미터로 넘긴다.
 */
@Service
public class StatsService {

	// 연속 학습일은 오늘을 포함한 최근 366일의 날짜만 조회해서 계산한다 (최대 366).
	public static final int STREAK_LOOKBACK_DAYS = 366;

	public static final int MAX_DAILY_RANGE_DAYS = 90;

	private final ReviewLogRepository reviewLogRepository;

	private final ReviewStateRepository reviewStateRepository;

	private final StudyProperties properties;

	private final Clock clock;

	public StatsService(ReviewLogRepository reviewLogRepository, ReviewStateRepository reviewStateRepository,
			StudyProperties properties, Clock clock) {
		this.reviewLogRepository = reviewLogRepository;
		this.reviewStateRepository = reviewStateRepository;
		this.properties = properties;
		this.clock = clock;
	}

	/** 쿼리 4개로 고정: 오늘 복습 수, 기한 지난 카드 수, 연속 학습일용 날짜 목록, 최근 7일 집계. */
	@Transactional(readOnly = true)
	public StatsSummaryResponse summary(Long memberId) {
		Instant now = clock.instant();
		LocalDate today = now.atZone(properties.zone()).toLocalDate();

		long todayReviews = reviewLogRepository.countByMemberIdAndReviewDate(memberId, today);
		long dueCards = reviewStateRepository.countByMemberIdAndDueAtLessThanEqual(memberId, now);
		int streak = streakDays(memberId, today);

		LocalDate weekStart = today.minusDays(6);
		ReviewCounts counts = reviewLogRepository.countReviews(memberId, weekStart, today, Rating.AGAIN);
		Double accuracy = counts.getReviews() == 0 ? null : (double) counts.getCorrect() / counts.getReviews();

		return new StatsSummaryResponse(today, properties.zone().getId(), todayReviews, dueCards, streak,
				new StatsSummaryResponse.Last7Days(weekStart, today, counts.getReviews(), counts.getCorrect(),
						accuracy));
	}

	/** GROUP BY review_date 한 번으로 계산하고, 기록이 없는 날은 0 으로 채운다. */
	@Transactional(readOnly = true)
	public DailyStatsResponse daily(Long memberId, LocalDate from, LocalDate to) {
		if (from.isAfter(to)) {
			throw BusinessException.invalidInput("from", "from 은 to 보다 늦을 수 없습니다.");
		}
		long days = ChronoUnit.DAYS.between(from, to) + 1;
		if (days > MAX_DAILY_RANGE_DAYS) {
			throw BusinessException.invalidInput("to", "조회 기간은 최대 " + MAX_DAILY_RANGE_DAYS + "일입니다.");
		}

		Map<LocalDate, Long> counts = reviewLogRepository.countDaily(memberId, from, to)
			.stream()
			.collect(Collectors.toMap(DailyReviewCount::getReviewDate, DailyReviewCount::getReviews));

		List<DailyStatsResponse.Day> result = new ArrayList<>((int) days);
		for (LocalDate date = from; !date.isAfter(to); date = date.plusDays(1)) {
			result.add(new DailyStatsResponse.Day(date, counts.getOrDefault(date, 0L)));
		}
		return new DailyStatsResponse(from, to, properties.zone().getId(), result);
	}

	/**
	 * 연속 학습일. 오늘 복습했으면 오늘부터, 아직 안 했으면 어제부터 거꾸로 끊김 없이 이어진 날을 센다.
	 * 오늘이 끝날 때까지는 어제까지의 연속이 유지되고, 하루를 건너뛰면 0 부터 다시 시작한다.
	 */
	private int streakDays(Long memberId, LocalDate today) {
		LocalDate from = today.minusDays(STREAK_LOOKBACK_DAYS - 1);
		Set<LocalDate> studied = new HashSet<>(reviewLogRepository.findReviewDates(memberId, from, today));

		LocalDate cursor = studied.contains(today) ? today : today.minusDays(1);
		int streak = 0;
		while (!cursor.isBefore(from) && studied.contains(cursor)) {
			streak++;
			cursor = cursor.minusDays(1);
		}
		return streak;
	}

}
