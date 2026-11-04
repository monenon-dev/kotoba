package com.kotoba.backend.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.closeTo;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

import com.kotoba.backend.domain.Card;
import com.kotoba.backend.domain.Deck;
import com.kotoba.backend.domain.Member;
import com.kotoba.backend.domain.ReviewLog;
import com.kotoba.backend.domain.ReviewState;
import com.kotoba.backend.srs.Rating;
import com.kotoba.backend.srs.ReviewSchedule;
import com.kotoba.backend.srs.ScheduleResult;
import com.kotoba.backend.support.IntegrationTestSupport;

class StatsApiTest extends IntegrationTestSupport {

	private static final String OWNER = "owner@example.com";

	private static final String OTHER = "other@example.com";

	// setUp 에서 Clock 을 서울 2026-11-04 10:00 으로 맞춘다.
	private static final LocalDate TODAY = LocalDate.of(2026, 11, 4);

	private String ownerToken;

	private Member owner;

	@BeforeEach
	void setUp() throws Exception {
		setSeoulTime(2026, 11, 4, 10, 0, 0);
		ownerToken = signupAndLogin(OWNER);
		owner = memberRepository.findByEmail(OWNER).orElseThrow();
	}

	// ===== summary =====

	@Test
	void summaryValues() throws Exception {
		insertLogs(owner, TODAY, Rating.GOOD, Rating.AGAIN, Rating.EASY);
		insertLogs(owner, TODAY.minusDays(1), Rating.GOOD, Rating.AGAIN);
		insertLogs(owner, TODAY.minusDays(6), Rating.HARD); // 최근 7일의 첫날 (포함)
		insertLogs(owner, TODAY.minusDays(7), Rating.GOOD); // 7일 범위 밖 (제외)
		insertStates(owner, 2, clock.instant().minusSeconds(1)); // 기한 지남
		insertStates(owner, 1, clock.instant().plusSeconds(60)); // 아직

		// 다른 회원의 기록은 섞이지 않는다.
		signupAndLogin(OTHER);
		Member other = memberRepository.findByEmail(OTHER).orElseThrow();
		insertLogs(other, TODAY, Rating.AGAIN, Rating.AGAIN);
		insertStates(other, 3, clock.instant().minusSeconds(1));

		summary(ownerToken).andExpect(status().isOk())
			.andExpect(jsonPath("$.today").value("2026-11-04"))
			.andExpect(jsonPath("$.timeZone").value("Asia/Seoul"))
			.andExpect(jsonPath("$.todayReviews").value(3))
			.andExpect(jsonPath("$.dueCards").value(2))
			.andExpect(jsonPath("$.streakDays").value(2))
			.andExpect(jsonPath("$.last7Days.from").value("2026-10-29"))
			.andExpect(jsonPath("$.last7Days.to").value("2026-11-04"))
			.andExpect(jsonPath("$.last7Days.reviews").value(6))
			.andExpect(jsonPath("$.last7Days.correct").value(4))
			.andExpect(jsonPath("$.last7Days.accuracy").value(closeTo(4.0 / 6, 1e-9)));
	}

	@Test
	void summaryWithoutReviews() throws Exception {
		summary(ownerToken).andExpect(jsonPath("$.todayReviews").value(0))
			.andExpect(jsonPath("$.streakDays").value(0))
			.andExpect(jsonPath("$.last7Days.reviews").value(0))
			.andExpect(jsonPath("$.last7Days.accuracy").value(nullValue()));
	}

	// ===== 연속 학습일 =====

	// 학습한 날을 "오늘로부터 며칠 전"으로 나열 (0 = 오늘)
	@ParameterizedTest(name = "[{index}] 학습한 날(며칠 전)=[{0}] -> {1}")
	@CsvSource(delimiter = '|', textBlock = """
			0 1 2      | 3
			1 2        | 2
			0          | 1
			1          | 1
			0 2 3      | 1
			2 3        | 0
			''         | 0
			0 1 2 4 5  | 3
			""")
	void streakRules(String daysAgo, int expected) throws Exception {
		Arrays.stream(daysAgo.trim().split("\\s+"))
			.filter(s -> !s.isEmpty())
			.forEach(d -> insertLogs(owner, TODAY.minusDays(Long.parseLong(d)), Rating.GOOD));

		summary(ownerToken).andExpect(jsonPath("$.streakDays").value(expected));
	}

	@Test
	void streakAcrossMidnightUsesStudyZone() throws Exception {
		Long deckId = createDeck(ownerToken, "N3");
		Long first = createCard(ownerToken, deckId, "一");
		Long second = createCard(ownerToken, deckId, "二");

		// 자정 직전과 직후의 복습은 서로 다른 날(서울 기준)로 기록된다.
		setSeoulTime(2026, 11, 4, 23, 59, 59);
		review(deckId, first);
		setSeoulTime(2026, 11, 5, 0, 0, 0);
		review(deckId, second);

		assertThat(reviewLogRepository.findAll()).extracting(ReviewLog::getReviewDate)
			.containsExactlyInAnyOrder(LocalDate.of(2026, 11, 4), LocalDate.of(2026, 11, 5));
		summary(ownerToken).andExpect(jsonPath("$.todayReviews").value(1))
			.andExpect(jsonPath("$.streakDays").value(2));

		// 11-06 은 아직 복습하지 않았어도 그날이 끝나기 직전까지 연속 유지
		setSeoulTime(2026, 11, 6, 23, 59, 59);
		ownerToken = login(OWNER);
		summary(ownerToken).andExpect(jsonPath("$.todayReviews").value(0))
			.andExpect(jsonPath("$.streakDays").value(2));

		// 11-07 자정: 11-06 을 건너뛰었으므로 0
		setSeoulTime(2026, 11, 7, 0, 0, 0);
		summary(ownerToken).andExpect(jsonPath("$.streakDays").value(0));
	}

	@Test
	void reviewDateUsesStudyZoneNotUtc() throws Exception {
		Long deckId = createDeck(ownerToken, "N3");
		Long cardId = createCard(ownerToken, deckId, "一");

		// 2026-11-04T15:30Z 는 UTC 로는 11-04 지만 서울로는 11-05 00:30
		clock.setNow(Instant.parse("2026-11-04T15:30:00Z"));
		review(deckId, cardId);

		assertThat(reviewLogRepository.findAll().getFirst().getReviewDate()).isEqualTo(LocalDate.of(2026, 11, 5));
	}

	@Test
	void streakIsCappedAt366Days() throws Exception {
		for (int i = 0; i < 400; i++) {
			insertLogs(owner, TODAY.minusDays(i), Rating.GOOD);
		}

		summary(ownerToken).andExpect(jsonPath("$.streakDays").value(366));
	}

	// ===== daily =====

	@Test
	void dailyFillsEmptyDaysWithZero() throws Exception {
		insertLogs(owner, LocalDate.of(2026, 11, 1), Rating.GOOD, Rating.AGAIN);
		insertLogs(owner, LocalDate.of(2026, 11, 3), Rating.EASY);
		insertLogs(owner, LocalDate.of(2026, 10, 31), Rating.GOOD); // 범위 밖
		signupAndLogin(OTHER);
		insertLogs(memberRepository.findByEmail(OTHER).orElseThrow(), LocalDate.of(2026, 11, 2), Rating.GOOD);

		daily(ownerToken, "2026-11-01", "2026-11-04").andExpect(status().isOk())
			.andExpect(jsonPath("$.days.length()").value(4))
			.andExpect(jsonPath("$.days[0].date").value("2026-11-01"))
			.andExpect(jsonPath("$.days[0].reviews").value(2))
			.andExpect(jsonPath("$.days[1].reviews").value(0))
			.andExpect(jsonPath("$.days[2].reviews").value(1))
			.andExpect(jsonPath("$.days[3].date").value("2026-11-04"))
			.andExpect(jsonPath("$.days[3].reviews").value(0));
	}

	@Test
	void dailyRangeValidation() throws Exception {
		// 90일(양 끝 포함)은 허용, 91일은 400
		daily(ownerToken, "2026-08-07", "2026-11-04").andExpect(status().isOk())
			.andExpect(jsonPath("$.days.length()").value(90));
		daily(ownerToken, "2026-08-06", "2026-11-04").andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("INVALID_INPUT"))
			.andExpect(jsonPath("$.errors[0].field").value("to"));
		daily(ownerToken, "2026-11-04", "2026-11-04").andExpect(jsonPath("$.days.length()").value(1));

		daily(ownerToken, "2026-11-05", "2026-11-04").andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.errors[0].field").value("from"));
		mockMvc.perform(get("/api/stats/daily").param("from", "2026-11-01")
			.header(HttpHeaders.AUTHORIZATION, bearer(ownerToken)))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.errors[0].field").value("to"));
		daily(ownerToken, "2026/11/01", "2026-11-04").andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.errors[0].field").value("from"));

		mockMvc.perform(get("/api/stats/summary")).andExpect(status().isUnauthorized());
	}

	// ===== 쿼리 수: 데이터 양에 비례하지 않음 =====

	@Test
	void statsQueryCountDoesNotGrowWithData() throws Exception {
		insertLogs(owner, TODAY, Rating.GOOD);
		insertStates(owner, 1, clock.instant().minusSeconds(1));
		long summarySmall = countQueries(() -> summary(ownerToken).andExpect(status().isOk()));
		long dailySmall = countQueries(() -> daily(ownerToken, "2026-09-01", "2026-11-04").andExpect(status().isOk()));

		for (int day = 0; day < 60; day++) {
			insertLogs(owner, TODAY.minusDays(day), Rating.GOOD, Rating.AGAIN, Rating.HARD, Rating.EASY, Rating.GOOD);
		}
		insertStates(owner, 50, clock.instant().minusSeconds(1));
		long summaryLarge = countQueries(() -> summary(ownerToken).andExpect(status().isOk())
			.andExpect(jsonPath("$.streakDays").value(60)));
		long dailyLarge = countQueries(() -> daily(ownerToken, "2026-09-01", "2026-11-04").andExpect(status().isOk()));

		assertThat(summaryLarge).isEqualTo(summarySmall).isLessThanOrEqualTo(4);
		assertThat(dailyLarge).isEqualTo(dailySmall).isLessThanOrEqualTo(1);
	}

	// ===== helpers =====

	private void insertLogs(Member member, LocalDate date, Rating... ratings) {
		Instant reviewedAt = date.atTime(12, 0).atZone(STUDY_ZONE).toInstant();
		List<ReviewLog> logs = new ArrayList<>();
		for (Rating rating : ratings) {
			logs.add(new ReviewLog(member, 1L, 1L, rating, false, 1, 3, reviewedAt, date));
		}
		reviewLogRepository.saveAll(logs);
	}

	private void insertStates(Member member, int count, Instant dueAt) {
		Deck deck = deckRepository.save(new Deck(member, "deck", null, clock.instant()));
		for (int i = 0; i < count; i++) {
			Card card = cardRepository.save(new Card(deck, "c" + i, null, "m", null, clock.instant()));
			reviewStateRepository.save(new ReviewState(card, deck.getId(), member.getId(),
					new ScheduleResult(new ReviewSchedule(1, 1, 2500, 0), clock.instant(), dueAt)));
		}
	}

	// Clock 을 옮긴 뒤 호출하므로 현재 시각 기준으로 다시 로그인해 토큰을 갱신한다.
	private void review(Long deckId, Long cardId) throws Exception {
		ownerToken = login(OWNER);
		mockMvc
			.perform(post("/api/decks/{deckId}/cards/{cardId}/reviews", deckId, cardId)
				.header(HttpHeaders.AUTHORIZATION, bearer(ownerToken))
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{"rating": "GOOD"}
						"""))
			.andExpect(status().isOk());
	}

	private ResultActions summary(String token) throws Exception {
		return mockMvc.perform(get("/api/stats/summary").header(HttpHeaders.AUTHORIZATION, bearer(token)))
			.andExpect(status().isOk());
	}

	private ResultActions daily(String token, String from, String to) throws Exception {
		return mockMvc.perform(
				get("/api/stats/daily").param("from", from).param("to", to).header(HttpHeaders.AUTHORIZATION, bearer(token)));
	}

}
