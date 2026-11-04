package com.kotoba.backend.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import com.kotoba.backend.domain.Card;
import com.kotoba.backend.domain.Deck;
import com.kotoba.backend.domain.ReviewLog;
import com.kotoba.backend.domain.ReviewState;
import com.kotoba.backend.exception.BusinessException;
import com.kotoba.backend.exception.ErrorCode;
import com.kotoba.backend.service.StudyService;
import com.kotoba.backend.srs.Rating;
import com.kotoba.backend.srs.ReviewSchedule;
import com.kotoba.backend.srs.ScheduleResult;
import com.kotoba.backend.support.IntegrationTestSupport;

class StudyApiTest extends IntegrationTestSupport {

	private static final String OWNER = "owner@example.com";

	private static final String OTHER = "other@example.com";

	@Autowired
	private StudyService studyService;

	@Autowired
	private TransactionTemplate transactionTemplate;

	private String ownerToken;

	private Long deckId;

	@BeforeEach
	void setUp() throws Exception {
		setSeoulTime(2026, 11, 4, 10, 0, 0);
		ownerToken = signupAndLogin(OWNER);
		deckId = createDeck(ownerToken, "N3");
	}

	// ===== 복습 =====

	@Test
	void firstReviewCreatesStateAndLog() throws Exception {
		Long cardId = createCard(ownerToken, deckId, "勉強");

		review(ownerToken, deckId, cardId, "GOOD").andExpect(status().isOk())
			.andExpect(jsonPath("$.cardId").value(cardId))
			.andExpect(jsonPath("$.rating").value("GOOD"))
			.andExpect(jsonPath("$.previousIntervalDays").doesNotExist())
			.andExpect(jsonPath("$.intervalDays").value(1))
			.andExpect(jsonPath("$.easeFactor").value(2.5))
			.andExpect(jsonPath("$.repetitions").value(1))
			.andExpect(jsonPath("$.lapses").value(0))
			// 서울 11-04 10:00 에 I=1 → 서울 11-05 00:00 = 11-04T15:00Z
			.andExpect(jsonPath("$.dueAt").value("2026-11-04T15:00:00Z"));

		ReviewState state = reviewStateRepository.findByCardId(cardId).orElseThrow();
		assertThat(state.getRepetitions()).isEqualTo(1);
		List<ReviewLog> logs = reviewLogRepository.findAll();
		assertThat(logs).hasSize(1);
		assertThat(logs.getFirst().isFirstReview()).isTrue();
		assertThat(logs.getFirst().getPreviousIntervalDays()).isNull();
		assertThat(logs.getFirst().getReviewDate()).hasToString("2026-11-04");

		// 두 번째 복습은 첫 복습이 아니다.
		clock.advance(Duration.ofDays(1));
		ownerToken = login(OWNER);
		review(ownerToken, deckId, cardId, "GOOD").andExpect(status().isOk())
			.andExpect(jsonPath("$.previousIntervalDays").value(1))
			.andExpect(jsonPath("$.intervalDays").value(3));
		assertThat(reviewLogRepository.findAll()).filteredOn(ReviewLog::isFirstReview).hasSize(1);
	}

	// ===== due 목록과 신규 한도 =====

	@Test
	void reviewingNewCardReducesRemainingLimitAndRemovesItFromDue() throws Exception {
		List<Long> cardIds = createCards(25);

		due(ownerToken, deckId, 100).andExpect(status().isOk())
			.andExpect(jsonPath("$.dueCount").value(0))
			.andExpect(jsonPath("$.newCount").value(20))
			.andExpect(jsonPath("$.remainingNewToday").value(20))
			// 신규 카드는 추가한 순서
			.andExpect(jsonPath("$.cards[0].cardId").value(cardIds.getFirst()))
			.andExpect(jsonPath("$.cards[0].isNew").value(true));

		Long reviewed = cardIds.getFirst();
		review(ownerToken, deckId, reviewed, "GOOD").andExpect(status().isOk());

		// 오늘 시작한 신규 카드 수 = 오늘 로그 중 first_review = true
		assertThat(reviewLogRepository.countByMemberIdAndReviewDateAndFirstReviewTrue(memberIdOf(OWNER),
				java.time.LocalDate.of(2026, 11, 4)))
			.isEqualTo(1);

		due(ownerToken, deckId, 100).andExpect(jsonPath("$.remainingNewToday").value(19))
			.andExpect(jsonPath("$.newCount").value(19))
			.andExpect(jsonPath("$.dueCount").value(0))
			.andExpect(jsonPath("$.cards[*].cardId", not(hasItem(reviewed.intValue()))));
	}

	@Test
	void reviewingStartedCardDoesNotUseNewLimit() throws Exception {
		// 어제 시작해서 오늘 기한이 된 카드 (상태 행 있음)
		Deck deck = deckRepository.findById(deckId).orElseThrow();
		Card started = cardRepository.save(new Card(deck, "started", null, "m", null, clock.instant()));
		Instant yesterday = clock.instant().minus(Duration.ofDays(1));
		reviewStateRepository.save(new ReviewState(started, deckId, memberIdOf(OWNER),
				new ScheduleResult(new ReviewSchedule(1, 1, 2500, 0), yesterday, clock.instant().minusSeconds(60))));

		review(ownerToken, deckId, started.getId(), "GOOD").andExpect(status().isOk());

		// first_review = false 인 복습이라 오늘 신규 한도는 그대로 20
		due(ownerToken, deckId, 20).andExpect(jsonPath("$.remainingNewToday").value(20));
		assertThat(reviewLogRepository.findAll()).singleElement()
			.satisfies(log -> assertThat(log.isFirstReview()).isFalse());
	}

	@Test
	void newLimitIsPerMemberAcrossDecksAndResetsNextDay() throws Exception {
		List<Long> cardIds = createCards(20);
		for (Long cardId : cardIds) {
			review(ownerToken, deckId, cardId, "GOOD").andExpect(status().isOk());
		}
		// 다른 덱이어도 회원 단위 한도라 오늘은 더 없다.
		Long otherDeck = createDeck(ownerToken, "N2");
		createCard(ownerToken, otherDeck, "新しい");
		due(ownerToken, otherDeck, 20).andExpect(jsonPath("$.remainingNewToday").value(0))
			.andExpect(jsonPath("$.newCount").value(0));

		// 다음 날 자정: 한도가 다시 차고, 어제 GOOD(I=1) 한 카드는 기한이 되어 due 로 나온다.
		setSeoulTime(2026, 11, 5, 0, 0, 0);
		ownerToken = login(OWNER);
		due(ownerToken, deckId, 100).andExpect(jsonPath("$.remainingNewToday").value(20))
			.andExpect(jsonPath("$.dueCount").value(20))
			.andExpect(jsonPath("$.cards[0].isNew").value(false));
		due(ownerToken, otherDeck, 20).andExpect(jsonPath("$.newCount").value(1));
	}

	@Test
	void dueAppearsExactlyAtMidnightInStudyZone() throws Exception {
		Long cardId = createCard(ownerToken, deckId, "勉強");

		setSeoulTime(2026, 11, 4, 23, 59, 0);
		ownerToken = login(OWNER);
		review(ownerToken, deckId, cardId, "GOOD").andExpect(status().isOk());

		// 자정 1초 전: 아직 기한 전이고, 이미 시작한 카드라 신규도 아니다.
		setSeoulTime(2026, 11, 4, 23, 59, 59);
		due(ownerToken, deckId, 20).andExpect(status().isOk()).andExpect(jsonPath("$.cards.length()").value(0));

		// 자정: 기한 도래
		setSeoulTime(2026, 11, 5, 0, 0, 0);
		due(ownerToken, deckId, 20).andExpect(jsonPath("$.dueCount").value(1))
			.andExpect(jsonPath("$.cards[0].cardId").value(cardId));
	}

	@Test
	void againReappearsAfterTenMinutes() throws Exception {
		Long cardId = createCard(ownerToken, deckId, "勉強");
		review(ownerToken, deckId, cardId, "AGAIN").andExpect(status().isOk())
			.andExpect(jsonPath("$.intervalDays").value(0))
			.andExpect(jsonPath("$.lapses").value(1));

		clock.advance(Duration.ofMinutes(9).plusSeconds(59));
		due(ownerToken, deckId, 20).andExpect(jsonPath("$.dueCount").value(0));

		clock.advance(Duration.ofSeconds(1));
		due(ownerToken, deckId, 20).andExpect(jsonPath("$.dueCount").value(1));
	}

	@Test
	void dueLimitIsCappedAndValidated() throws Exception {
		Deck deck = deckRepository.findById(deckId).orElseThrow();
		Long memberId = memberIdOf(OWNER);
		Instant past = clock.instant().minusSeconds(3600);
		for (int i = 0; i < 120; i++) {
			Card card = cardRepository.save(new Card(deck, "c" + i, null, "m", null, past));
			reviewStateRepository.save(new ReviewState(card, deckId, memberId,
					new ScheduleResult(new ReviewSchedule(1, 1, 2500, 0), past, past.plusSeconds(i))));
		}

		due(ownerToken, deckId, 1000).andExpect(status().isOk())
			.andExpect(jsonPath("$.dueCount").value(100))
			.andExpect(jsonPath("$.cards.length()").value(100));
		// 기본 limit 20
		mockMvc.perform(get("/api/decks/{deckId}/study/due", deckId).header(HttpHeaders.AUTHORIZATION, bearer(ownerToken)))
			.andExpect(jsonPath("$.cards.length()").value(20));
		due(ownerToken, deckId, 0).andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("INVALID_INPUT"))
			.andExpect(jsonPath("$.errors[0].field").value("limit"));
	}

	// ===== 삭제와 기록 보존 =====

	@Test
	void deletingDeckRemovesStatesButKeepsLogsAndTodaysNewCount() throws Exception {
		Long cardId = createCard(ownerToken, deckId, "勉強");
		review(ownerToken, deckId, cardId, "GOOD").andExpect(status().isOk());

		mockMvc.perform(delete("/api/decks/{deckId}", deckId).header(HttpHeaders.AUTHORIZATION, bearer(ownerToken)))
			.andExpect(status().isNoContent());

		assertThat(reviewStateRepository.count()).isZero();
		assertThat(reviewLogRepository.count()).isEqualTo(1);
		// 덱을 지워도 오늘 시작한 신규 수는 되돌아오지 않는다.
		Long newDeck = createDeck(ownerToken, "new");
		due(ownerToken, newDeck, 20).andExpect(jsonPath("$.remainingNewToday").value(19));
	}

	@Test
	void deletingCardRemovesStateButKeepsLog() throws Exception {
		Long cardId = createCard(ownerToken, deckId, "勉強");
		review(ownerToken, deckId, cardId, "GOOD").andExpect(status().isOk());

		mockMvc
			.perform(delete("/api/decks/{deckId}/cards/{cardId}", deckId, cardId)
				.header(HttpHeaders.AUTHORIZATION, bearer(ownerToken)))
			.andExpect(status().isNoContent());

		assertThat(reviewStateRepository.findByCardId(cardId)).isEmpty();
		assertThat(cardRepository.existsById(cardId)).isFalse();
		assertThat(reviewLogRepository.count()).isEqualTo(1);
	}

	// ===== 소유자 권한 / 유효성 =====

	@Test
	void otherMembersDeckAndCardReturn404() throws Exception {
		Long cardId = createCard(ownerToken, deckId, "勉強");
		String otherToken = signupAndLogin(OTHER);

		due(otherToken, deckId, 20).andExpect(status().isNotFound())
			.andExpect(jsonPath("$.code").value("DECK_NOT_FOUND"));
		review(otherToken, deckId, cardId, "GOOD").andExpect(status().isNotFound())
			.andExpect(jsonPath("$.code").value("CARD_NOT_FOUND"));

		// 내 카드라도 URL 의 덱에 속하지 않으면 404
		Long anotherDeck = createDeck(ownerToken, "another");
		review(ownerToken, anotherDeck, cardId, "GOOD").andExpect(status().isNotFound());

		assertThat(reviewStateRepository.count()).isZero();
		assertThat(reviewLogRepository.count()).isZero();
	}

	@Test
	void reviewValidation() throws Exception {
		Long cardId = createCard(ownerToken, deckId, "勉強");

		reviewRaw(ownerToken, deckId, cardId, "{}").andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("INVALID_INPUT"))
			.andExpect(jsonPath("$.errors[0].field").value("rating"));
		reviewRaw(ownerToken, deckId, cardId, """
				{"rating": "WRONG"}
				""").andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("MALFORMED_REQUEST"));

		mockMvc.perform(get("/api/decks/{deckId}/study/due", deckId)).andExpect(status().isUnauthorized());
		assertThat(reviewLogRepository.count()).isZero();
	}

	// ===== 동시성 =====

	/**
	 * 결정적으로 재현한 동시 복습: 트랜잭션 A 가 상태를 읽은 뒤(버전 v), 다른 요청 B 가 같은 카드를 복습해 커밋하고(버전 v+1),
	 * 그 다음 A 가 갱신을 시도한다. A 는 낡은 버전으로 UPDATE 하므로 0행 → 409 REVIEW_CONFLICT, A 의 로그는 롤백된다.
	 */
	@Test
	void concurrentReviewOfSameCardConflicts() throws Exception {
		Long cardId = createCard(ownerToken, deckId, "勉強");
		review(ownerToken, deckId, cardId, "GOOD").andExpect(status().isOk());
		Long memberId = memberIdOf(OWNER);
		ExecutorService executor = Executors.newSingleThreadExecutor();

		try {
			assertThatThrownBy(() -> transactionTemplate.executeWithoutResult(tx -> {
				// A: 상태를 먼저 읽어 영속성 컨텍스트에 둔다 (버전 v).
				ReviewState stale = reviewStateRepository.findByCardId(cardId).orElseThrow();
				assertThat(stale.getVersion()).isZero();

				// B: 다른 스레드(별도 트랜잭션)에서 같은 카드를 복습하고 커밋한다 (버전 v+1).
				Future<Integer> other = executor
					.submit(() -> review(ownerToken, deckId, cardId, "GOOD").andReturn().getResponse().getStatus());
				assertThat(await(other)).isEqualTo(200);

				// A: 낡은 상태로 갱신 시도 → 충돌
				studyService.review(memberId, deckId, cardId, Rating.GOOD);
			})).isInstanceOfSatisfying(BusinessException.class,
					e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.REVIEW_CONFLICT));
		}
		finally {
			executor.shutdownNow();
		}

		// 반영된 것은 첫 복습과 B 뿐이다.
		ReviewState state = reviewStateRepository.findByCardId(cardId).orElseThrow();
		assertThat(state.getRepetitions()).isEqualTo(2);
		assertThat(state.getVersion()).isEqualTo(1);
		assertThat(reviewLogRepository.count()).isEqualTo(2);
	}

	/**
	 * 실제 HTTP 요청 여러 개를 동시에 보낸다. 몇 개가 409 가 될지는 타이밍에 따라 다르지만,
	 * 어떤 경우에도 "200 응답 수 = 반영된 복습 수 = 저장된 로그 수" 여야 한다 (갱신 유실 없음).
	 */
	@Test
	void parallelReviewsNeverLoseUpdates() throws Exception {
		Long cardId = createCard(ownerToken, deckId, "勉強");
		review(ownerToken, deckId, cardId, "GOOD").andExpect(status().isOk());

		int threads = 8;
		ExecutorService executor = Executors.newFixedThreadPool(threads);
		CountDownLatch start = new CountDownLatch(1);
		List<Future<Integer>> results = new ArrayList<>();
		try {
			for (int i = 0; i < threads; i++) {
				results.add(executor.submit(() -> {
					start.await();
					return review(ownerToken, deckId, cardId, "GOOD").andReturn().getResponse().getStatus();
				}));
			}
			start.countDown();
			List<Integer> statuses = new ArrayList<>();
			for (Future<Integer> result : results) {
				statuses.add(await(result));
			}
			// CI 에서 실패해도 원인을 볼 수 있게 응답 상태 목록을 메시지에 남긴다.
			String context = "응답 상태 " + statuses;
			long ok = statuses.stream().filter(s -> s == 200).count();

			assertThat(statuses).as(context).allMatch(s -> s == 200 || s == 409);
			assertThat(ok).as(context).isGreaterThanOrEqualTo(1);
			ReviewState state = reviewStateRepository.findByCardId(cardId).orElseThrow();
			assertThat(state.getRepetitions()).as(context).isEqualTo(1 + ok);
			assertThat(reviewLogRepository.count()).as(context).isEqualTo(1 + ok);
		}
		finally {
			executor.shutdownNow();
		}
	}

	/**
	 * 신규 카드의 첫 복습이 동시에 들어오는 경로를 결정적으로 재현한다 (UNIQUE(card_id) 위반 → 409).
	 *
	 * READ COMMITTED 에서는 늦게 조회한 쪽이 먼저 커밋된 상태를 보고 "두 번째 복습"으로 정상 처리되므로,
	 * A 를 REPEATABLE READ 로 열어 "상태 없음"을 본 스냅샷을 고정한다.
	 * 순서: A 가 상태 없음을 읽음 → B(별도 스레드, HTTP)가 첫 복습을 커밋 → A 가 첫 복습으로 INSERT → UNIQUE 위반.
	 */
	@Test
	void concurrentFirstReviewOfNewCardConflictsOnUniqueKey() throws Exception {
		Long cardId = createCard(ownerToken, deckId, "新しい");
		Long memberId = memberIdOf(OWNER);
		TransactionTemplate repeatableRead = new TransactionTemplate(transactionTemplate.getTransactionManager());
		repeatableRead.setIsolationLevel(TransactionDefinition.ISOLATION_REPEATABLE_READ);
		ExecutorService executor = Executors.newSingleThreadExecutor();

		try {
			assertThatThrownBy(() -> repeatableRead.executeWithoutResult(tx -> {
				// A: 신규 카드라 상태가 없음을 읽는다. 이 시점의 스냅샷이 트랜잭션 끝까지 유지된다.
				assertThat(reviewStateRepository.findByCardId(cardId)).isEmpty();

				// B: 다른 스레드에서 같은 카드의 첫 복습을 커밋한다.
				Future<Integer> other = executor
					.submit(() -> review(ownerToken, deckId, cardId, "GOOD").andReturn().getResponse().getStatus());
				assertThat(await(other)).isEqualTo(200);

				// A: 스냅샷상 여전히 상태가 없으므로 첫 복습으로 INSERT → UNIQUE(card_id) 위반
				studyService.review(memberId, deckId, cardId, Rating.GOOD);
			})).isInstanceOfSatisfying(BusinessException.class,
					e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.REVIEW_CONFLICT));
		}
		finally {
			executor.shutdownNow();
		}

		// 반영된 것은 B 뿐: 상태 1행, 로그 1건(첫 복습), A 의 로그는 롤백
		assertThat(reviewStateRepository.findAll()).filteredOn(s -> s.getCard().getId().equals(cardId)).hasSize(1);
		assertThat(reviewStateRepository.findByCardId(cardId).orElseThrow().getRepetitions()).isEqualTo(1);
		assertThat(reviewLogRepository.findAll()).filteredOn(log -> log.getCardId().equals(cardId))
			.singleElement()
			.satisfies(log -> assertThat(log.isFirstReview()).isTrue());
	}

	/**
	 * 아직 한 번도 복습하지 않은 카드에 첫 복습 요청 8개를 동시에 보낸다.
	 *
	 * 결과 개수는 타이밍에 따라 다르다. 모두가 "상태 없음"을 본 뒤 경쟁하면 1개 성공 + 나머지 UNIQUE 위반(409)이고,
	 * 첫 커밋 뒤에 조회한 요청은 "두 번째 복습"으로 정상 처리되어 200 이 된다 (올바른 동작).
	 * 그래서 다음 불변식만 검증한다:
	 * 500 없음(200/409 뿐), 상태 행은 카드당 1개, 첫 복습 로그는 정확히 1건, 로그 수 = 200 수 = 연속 성공 횟수.
	 */
	@Test
	void parallelFirstReviewsOfNewCardKeepInvariants() throws Exception {
		Long cardId = createCard(ownerToken, deckId, "新しい");

		int threads = 8;
		ExecutorService executor = Executors.newFixedThreadPool(threads);
		CountDownLatch start = new CountDownLatch(1);
		List<Future<Integer>> results = new ArrayList<>();
		try {
			for (int i = 0; i < threads; i++) {
				results.add(executor.submit(() -> {
					start.await();
					return review(ownerToken, deckId, cardId, "GOOD").andReturn().getResponse().getStatus();
				}));
			}
			start.countDown();
			List<Integer> statuses = new ArrayList<>();
			for (Future<Integer> result : results) {
				statuses.add(await(result));
			}
			long ok = statuses.stream().filter(s -> s == 200).count();
			String context = "응답 상태 " + statuses;
			// 반복 실행 때 실제로 어떤 분포가 나왔는지 리포트에서 볼 수 있게 남긴다.
			System.out.println("[parallelFirstReviews] " + context);

			assertThat(statuses).as(context).allMatch(s -> s == 200 || s == 409);
			assertThat(ok).as(context).isGreaterThanOrEqualTo(1);
			assertThat(reviewStateRepository.findAll()).as(context)
				.filteredOn(s -> s.getCard().getId().equals(cardId))
				.hasSize(1);
			List<ReviewLog> logs = reviewLogRepository.findAll()
				.stream()
				.filter(log -> log.getCardId().equals(cardId))
				.toList();
			assertThat(logs).as(context).filteredOn(ReviewLog::isFirstReview).hasSize(1);
			assertThat(logs).as(context).hasSize((int) ok);
			assertThat(reviewStateRepository.findByCardId(cardId).orElseThrow().getRepetitions()).as(context)
				.isEqualTo((int) ok);
		}
		finally {
			executor.shutdownNow();
		}
	}

	// ===== helpers =====

	private List<Long> createCards(int count) throws Exception {
		List<Long> ids = new ArrayList<>();
		for (int i = 0; i < count; i++) {
			ids.add(createCard(ownerToken, deckId, "card-" + i));
			clock.advance(Duration.ofSeconds(1));
		}
		return ids;
	}

	private ResultActions due(String token, Long deckId, int limit) throws Exception {
		return mockMvc.perform(get("/api/decks/{deckId}/study/due", deckId).param("limit", String.valueOf(limit))
			.header(HttpHeaders.AUTHORIZATION, bearer(token)));
	}

	private ResultActions review(String token, Long deckId, Long cardId, String rating) throws Exception {
		return reviewRaw(token, deckId, cardId, """
				{"rating": "%s"}
				""".formatted(rating));
	}

	private ResultActions reviewRaw(String token, Long deckId, Long cardId, String json) throws Exception {
		return mockMvc.perform(post("/api/decks/{deckId}/cards/{cardId}/reviews", deckId, cardId)
			.header(HttpHeaders.AUTHORIZATION, bearer(token))
			.contentType(MediaType.APPLICATION_JSON)
			.content(json));
	}

	private static <T> T await(Future<T> future) {
		try {
			return future.get(30, java.util.concurrent.TimeUnit.SECONDS);
		}
		catch (Exception e) {
			throw new IllegalStateException(e);
		}
	}

}
