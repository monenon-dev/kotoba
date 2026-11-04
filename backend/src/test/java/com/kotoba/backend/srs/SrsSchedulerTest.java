package com.kotoba.backend.srs;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import com.kotoba.backend.support.MutableClock;

/**
 * 순수 클래스 단위 테스트. Spring 컨텍스트 없이 MutableClock 으로 시간을 고정한다.
 */
class SrsSchedulerTest {

	private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");

	private final MutableClock clock = new MutableClock(seoul(2026, 11, 4, 10, 0));

	private final SrsScheduler scheduler = new SrsScheduler(clock, SEOUL);

	// 입력 상태 (n, I, EF x1000, lapses) + 평가 → 기대 상태
	@ParameterizedTest(name = "[{index}] n={0} I={1} EF={2} lapses={3} {4} -> n={5} I={6} EF={7} lapses={8}")
	@CsvSource(textBlock = """
			# 신규 카드
			0, 0,   2500, 0, AGAIN, 0, 0,   2300, 1
			0, 0,   2500, 0, HARD,  1, 1,   2350, 0
			0, 0,   2500, 0, GOOD,  1, 1,   2500, 0
			0, 0,   2500, 0, EASY,  1, 4,   2650, 0
			# 두 번째 복습 (n=1, I=1)
			1, 1,   2500, 0, HARD,  2, 1,   2350, 0
			1, 1,   2500, 0, GOOD,  2, 3,   2500, 0
			1, 1,   2500, 0, EASY,  2, 4,   2650, 0
			# 첫 복습을 EASY 로 한 카드(n=1, I=4): GOOD 이 간격을 줄이지 않고(5), HARD 는 GOOD 을 넘지 않는다(5)
			1, 4,   2650, 0, GOOD,  2, 5,   2650, 0
			1, 4,   2650, 0, HARD,  2, 5,   2500, 0
			1, 4,   2650, 0, EASY,  2, 14,  2800, 0
			# 세 번째 이후 (n=2, I=3): HARD=round(3*1.2)=4, GOOD=round(3*2.5)=8, EASY=round(3*2.5*1.3)=10
			2, 3,   2500, 0, HARD,  3, 4,   2350, 0
			2, 3,   2500, 0, GOOD,  3, 8,   2500, 0
			2, 3,   2500, 0, EASY,  3, 10,  2650, 0
			2, 3,   2500, 0, AGAIN, 0, 0,   2300, 1
			2, 10,  2500, 0, HARD,  3, 12,  2350, 0
			3, 8,   2500, 0, GOOD,  4, 20,  2500, 0
			# 실패 횟수는 누적
			3, 8,   2500, 1, AGAIN, 0, 0,   2300, 2
			# 난이도 계수 하한 1.3
			2, 3,   1300, 0, AGAIN, 0, 0,   1300, 1
			2, 3,   1300, 0, HARD,  3, 4,   1300, 0
			2, 3,   1300, 0, GOOD,  3, 4,   1300, 0
			# 하한에서도 GOOD 은 최소 I+1 (round(1*1.3)=1 이지만 2)
			2, 1,   1300, 0, GOOD,  3, 2,   1300, 0
			2, 1,   1300, 0, HARD,  3, 1,   1300, 0
			# 간격 상한 365
			5, 300, 2500, 0, HARD,  6, 360, 2350, 0
			5, 300, 2500, 0, GOOD,  6, 365, 2500, 0
			5, 300, 2500, 0, EASY,  6, 365, 2650, 0
			""")
	void scheduleTable(int n, int interval, int ease, int lapses, Rating rating, int expectedN,
			int expectedInterval, int expectedEase, int expectedLapses) {
		ScheduleResult result = scheduler.schedule(new ReviewSchedule(n, interval, ease, lapses), rating);

		assertThat(result.schedule())
			.isEqualTo(new ReviewSchedule(expectedN, expectedInterval, expectedEase, expectedLapses));
	}

	@Test
	void againIsDueTenMinutesLater() {
		ScheduleResult result = scheduler.schedule(new ReviewSchedule(3, 8, 2500, 0), Rating.AGAIN);

		assertThat(result.reviewedAt()).isEqualTo(clock.instant());
		assertThat(result.dueAt()).isEqualTo(clock.instant().plusSeconds(600));
	}

	@Test
	void dueIsAlignedToMidnightInStudyZone() {
		// 23:59 에 신규 카드를 GOOD(I=1) → 다음 날 00:00 (1분 뒤)
		clock.setNow(seoul(2026, 11, 4, 23, 59));
		ScheduleResult result = scheduler.schedule(ReviewSchedule.NEW, Rating.GOOD);
		assertThat(result.dueAt()).isEqualTo(seoul(2026, 11, 5, 0, 0));

		// 자정 직후 00:00 에 GOOD → 같은 규칙으로 다음 날 00:00 (하루 뒤)
		clock.setNow(seoul(2026, 11, 5, 0, 0));
		assertThat(scheduler.schedule(ReviewSchedule.NEW, Rating.GOOD).dueAt()).isEqualTo(seoul(2026, 11, 6, 0, 0));

		// I=8 → 8일 뒤 자정
		clock.setNow(seoul(2026, 11, 4, 15, 30));
		ScheduleResult eightDays = scheduler.schedule(new ReviewSchedule(2, 3, 2500, 0), Rating.GOOD);
		assertThat(eightDays.schedule().intervalDays()).isEqualTo(8);
		assertThat(eightDays.dueAt()).isEqualTo(seoul(2026, 11, 12, 0, 0));
	}

	@Test
	void dayBoundaryFollowsConfiguredZone() {
		// 2026-11-04T15:30Z = 서울 11-05 00:30 = UTC 11-04 15:30. 같은 순간이라도 시간대에 따라 "오늘"이 다르다.
		Instant instant = Instant.parse("2026-11-04T15:30:00Z");
		clock.setNow(instant);

		Instant seoulDue = new SrsScheduler(clock, SEOUL).schedule(ReviewSchedule.NEW, Rating.GOOD).dueAt();
		Instant utcDue = new SrsScheduler(clock, ZoneOffset.UTC).schedule(ReviewSchedule.NEW, Rating.GOOD).dueAt();

		assertThat(seoulDue).isEqualTo(seoul(2026, 11, 6, 0, 0));
		assertThat(utcDue).isEqualTo(Instant.parse("2026-11-05T00:00:00Z"));
	}

	@Test
	void hardIsNotLongerThanGoodAndEasyIsLongerThanGood() {
		for (int n = 1; n <= 5; n++) {
			for (int interval : new int[] { 1, 2, 3, 5, 10, 50, 200 }) {
				for (int ease : new int[] { 1300, 1800, 2500, 3200 }) {
					ReviewSchedule current = new ReviewSchedule(n, interval, ease, 0);
					int hard = scheduler.schedule(current, Rating.HARD).schedule().intervalDays();
					int good = scheduler.schedule(current, Rating.GOOD).schedule().intervalDays();
					int easy = scheduler.schedule(current, Rating.EASY).schedule().intervalDays();

					String context = "n=%d I=%d EF=%d".formatted(n, interval, ease);
					assertThat(hard).as(context).isLessThanOrEqualTo(good);
					if (good < SrsScheduler.MAX_INTERVAL_DAYS) {
						assertThat(easy).as(context).isGreaterThan(good);
						assertThat(good).as(context).isGreaterThan(interval);
					}
				}
			}
		}
	}

	@Test
	void repeatedGoodFollowsDocumentedExample() {
		// 설계 문서의 예시: 1 → 3 → 8 → 20
		ReviewSchedule state = ReviewSchedule.NEW;
		int[] expected = { 1, 3, 8, 20 };
		for (int expectedInterval : expected) {
			state = scheduler.schedule(state, Rating.GOOD).schedule();
			assertThat(state.intervalDays()).isEqualTo(expectedInterval);
		}
	}

	@Test
	void documentedThreeReviewExample() {
		// 설계 문서 12절 (1): GOOD → EASY → AGAIN
		ScheduleResult first = scheduler.schedule(ReviewSchedule.NEW, Rating.GOOD);
		assertThat(first.schedule()).isEqualTo(new ReviewSchedule(1, 1, 2500, 0));
		assertThat(first.dueAt()).isEqualTo(seoul(2026, 11, 5, 0, 0));

		ScheduleResult second = scheduler.schedule(first.schedule(), Rating.EASY);
		assertThat(second.schedule()).isEqualTo(new ReviewSchedule(2, 4, 2650, 0));
		assertThat(second.dueAt()).isEqualTo(seoul(2026, 11, 8, 0, 0));

		ScheduleResult third = scheduler.schedule(second.schedule(), Rating.AGAIN);
		assertThat(third.schedule()).isEqualTo(new ReviewSchedule(0, 0, 2450, 1));
		assertThat(third.dueAt()).isEqualTo(clock.instant().plusSeconds(600));
	}

	private static Instant seoul(int year, int month, int day, int hour, int minute) {
		return LocalDateTime.of(year, month, day, hour, minute).atZone(SEOUL).toInstant();
	}

}
