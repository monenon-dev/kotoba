package com.kotoba.backend.srs;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;

/**
 * SM-2 를 단순화한 복습 스케줄러. Spring 에 의존하지 않는 순수 클래스다.
 *
 * <pre>
 * 평가    새 n     새 EF                 새 간격 I'
 * AGAIN  0       max(1.3, EF - 0.20)   0, 10분 뒤 다시 (lapses + 1)
 * HARD   n + 1   max(1.3, EF - 0.15)   n = 0: 1 / n >= 1: min(GOOD, max(1, round(I * 1.2)))
 * GOOD   n + 1   EF                    n = 0: 1 / n = 1: max(3, I + 1) / n >= 2: max(I + 1, round(I * EF))
 * EASY   n + 1   EF + 0.15             n = 0: 4 / n >= 1: max(GOOD + 1, round(I * EF * 1.3))
 * </pre>
 * 보장: n >= 1 이면 HARD <= GOOD < EASY 이고, GOOD 은 항상 현재 간격보다 길다.
 * (첫 복습을 EASY 로 하면 n = 1, I = 4 가 되므로 n = 1 의 GOOD 을 3 으로 고정하면 간격이 줄어든다. 그래서 max(3, I + 1))
 * 간격은 365일로 자른다. 간격이 1일 이상이면 다음 복습 시각은 (오늘 + I') 의 자정(기준 시간대)이다.
 * 계수는 x1000 정수로 다루고, round 는 반올림(half-up)을 정수 연산으로 계산한다.
 */
public class SrsScheduler {

	public static final int EASE_INITIAL = 2500;

	public static final int EASE_MIN = 1300;

	public static final int MAX_INTERVAL_DAYS = 365;

	public static final Duration RELEARN_DELAY = Duration.ofMinutes(10);

	private static final int AGAIN_EASE_PENALTY = 200;

	private static final int HARD_EASE_PENALTY = 150;

	private static final int EASY_EASE_BONUS = 150;

	private final Clock clock;

	private final ZoneId zone;

	public SrsScheduler(Clock clock, ZoneId zone) {
		this.clock = clock;
		this.zone = zone;
	}

	public ScheduleResult schedule(ReviewSchedule current, Rating rating) {
		Instant now = clock.instant();
		if (rating == Rating.AGAIN) {
			ReviewSchedule next = new ReviewSchedule(0, 0,
					Math.max(EASE_MIN, current.easePermille() - AGAIN_EASE_PENALTY), current.lapses() + 1);
			return new ScheduleResult(next, now, now.plus(RELEARN_DELAY));
		}

		int n = current.repetitions();
		int interval = current.intervalDays();
		int ease = current.easePermille();

		int nextInterval;
		int nextEase;
		switch (rating) {
			case HARD -> {
				nextInterval = n == 0 ? 1
						: Math.min(goodInterval(n, interval, ease), Math.max(1, roundDiv(interval * 12L, 10)));
				nextEase = Math.max(EASE_MIN, ease - HARD_EASE_PENALTY);
			}
			case GOOD -> {
				nextInterval = goodInterval(n, interval, ease);
				nextEase = ease;
			}
			case EASY -> {
				nextInterval = n == 0 ? 4
						: Math.max(goodInterval(n, interval, ease) + 1, roundDiv((long) interval * ease * 13, 10_000));
				nextEase = ease + EASY_EASE_BONUS;
			}
			default -> throw new IllegalArgumentException("Unexpected rating: " + rating);
		}
		nextInterval = Math.min(nextInterval, MAX_INTERVAL_DAYS);

		ReviewSchedule next = new ReviewSchedule(n + 1, nextInterval, nextEase, current.lapses());
		return new ScheduleResult(next, now, startOfDayAfter(now, nextInterval));
	}

	public ZoneId zone() {
		return zone;
	}

	private static int goodInterval(int n, int interval, int ease) {
		if (n == 0) {
			return 1;
		}
		if (n == 1) {
			return Math.max(3, interval + 1);
		}
		// 계수가 하한(1.3)이고 간격이 1일 때도 간격이 늘어나도록 최소 I + 1 을 보장한다.
		return Math.max(interval + 1, roundDiv((long) interval * ease, 1000));
	}

	// "그날 자정부터" 복습할 수 있게 기준 시간대의 날짜 경계로 맞춘다.
	private Instant startOfDayAfter(Instant now, int days) {
		LocalDate today = now.atZone(zone).toLocalDate();
		return today.plusDays(days).atStartOfDay(zone).toInstant();
	}

	// 양수 나눗셈의 반올림(half-up). 예: roundDiv(75, 10) = 8
	private static int roundDiv(long numerator, long denominator) {
		return Math.toIntExact((numerator + denominator / 2) / denominator);
	}

}
