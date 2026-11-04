package com.kotoba.backend.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import com.kotoba.backend.domain.Deck;
import com.kotoba.backend.domain.Member;
import com.kotoba.backend.support.IntegrationTestSupport;

class DeckApiTest extends IntegrationTestSupport {

	private static final String OWNER = "owner@example.com";

	private static final String OTHER = "other@example.com";

	private String ownerToken;

	@BeforeEach
	void setUp() throws Exception {
		ownerToken = signupAndLogin(OWNER);
	}

	// ===== 정상 CRUD =====

	@Test
	void createAndGetDeck() throws Exception {
		perform(post("/api/decks"), ownerToken, """
				{"name": "JLPT N3", "description": "N3 단어"}
				""")
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.id").isNumber())
			.andExpect(jsonPath("$.name").value("JLPT N3"))
			.andExpect(jsonPath("$.description").value("N3 단어"))
			.andExpect(jsonPath("$.cardCount").value(0))
			.andExpect(jsonPath("$.createdAt").isString())
			.andExpect(jsonPath("$.updatedAt").isString());

		Long deckId = deckRepository.findAll().getFirst().getId();
		createCard(ownerToken, deckId, "勉強");

		perform(get("/api/decks/{deckId}", deckId), ownerToken)
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.name").value("JLPT N3"))
			.andExpect(jsonPath("$.cardCount").value(1));
	}

	@Test
	void updateDeckReplacesAllFields() throws Exception {
		Long deckId = createDeck(ownerToken, "old");
		clock.advance(Duration.ofMinutes(5));

		// PUT 은 전체 교체: description 을 생략하면 null 이 된다.
		perform(put("/api/decks/{deckId}", deckId), ownerToken, """
				{"name": "new"}
				""")
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.name").value("new"))
			.andExpect(jsonPath("$.description").doesNotExist());

		Deck deck = deckRepository.findById(deckId).orElseThrow();
		assertThat(deck.getName()).isEqualTo("new");
		assertThat(deck.getDescription()).isNull();
		assertThat(deck.getUpdatedAt()).isAfter(deck.getCreatedAt());
	}

	@Test
	void deleteDeckAlsoDeletesItsCardsOnly() throws Exception {
		Long target = createDeck(ownerToken, "target");
		Long kept = createDeck(ownerToken, "kept");
		for (int i = 0; i < 3; i++) {
			createCard(ownerToken, target, "target-" + i);
		}
		createCard(ownerToken, kept, "kept-0");
		createCard(ownerToken, kept, "kept-1");

		perform(delete("/api/decks/{deckId}", target), ownerToken).andExpect(status().isNoContent());

		assertThat(deckRepository.existsById(target)).isFalse();
		assertThat(cardRepository.countByDeckId(target)).isZero();
		// 다른 덱의 카드는 그대로 남는다.
		assertThat(cardRepository.countByDeckId(kept)).isEqualTo(2);
		perform(get("/api/decks/{deckId}", target), ownerToken).andExpect(status().isNotFound());
	}

	// ===== 목록, 카드 수, 페이지네이션 =====

	@Test
	void listShowsCardCountIncludingEmptyDecks() throws Exception {
		Long withCards = createDeck(ownerToken, "with-cards");
		createCard(ownerToken, withCards, "a");
		createCard(ownerToken, withCards, "b");
		clock.advance(Duration.ofSeconds(1));
		Long empty = createDeck(ownerToken, "empty");

		perform(get("/api/decks"), ownerToken)
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.content.length()").value(2))
			// 최신순: empty 가 먼저
			.andExpect(jsonPath("$.content[0].id").value(empty))
			// 카드가 0장인 덱도 GROUP BY 결과에서 빠지지 않고 0 으로 나온다.
			.andExpect(jsonPath("$.content[0].cardCount").value(0))
			.andExpect(jsonPath("$.content[1].id").value(withCards))
			.andExpect(jsonPath("$.content[1].cardCount").value(2));
	}

	@Test
	void listOnlyShowsOwnDecks() throws Exception {
		createDeck(ownerToken, "mine");
		String otherToken = signupAndLogin(OTHER);
		createDeck(otherToken, "theirs");

		perform(get("/api/decks"), ownerToken)
			.andExpect(jsonPath("$.totalElements").value(1))
			.andExpect(jsonPath("$.content[0].name").value("mine"));
	}

	@Test
	void listIsPaginatedNewestFirst() throws Exception {
		for (int i = 0; i < 25; i++) {
			createDeck(ownerToken, "deck-" + i);
			clock.advance(Duration.ofSeconds(1));
		}

		perform(get("/api/decks?page=0&size=10"), ownerToken)
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.content.length()").value(10))
			.andExpect(jsonPath("$.content[0].name").value("deck-24"))
			.andExpect(jsonPath("$.page").value(0))
			.andExpect(jsonPath("$.size").value(10))
			.andExpect(jsonPath("$.totalElements").value(25))
			.andExpect(jsonPath("$.totalPages").value(3))
			.andExpect(jsonPath("$.hasNext").value(true));

		perform(get("/api/decks?page=2&size=10"), ownerToken)
			.andExpect(jsonPath("$.content.length()").value(5))
			.andExpect(jsonPath("$.content[4].name").value("deck-0"))
			.andExpect(jsonPath("$.hasNext").value(false));
	}

	@Test
	void pageSizeIsCappedAt100() throws Exception {
		Member owner = memberRepository.findByEmail(OWNER).orElseThrow();
		List<Deck> decks = new ArrayList<>();
		for (int i = 0; i < 120; i++) {
			decks.add(new Deck(owner, "deck-" + i, null, clock.instant().plusSeconds(i)));
		}
		deckRepository.saveAll(decks);

		perform(get("/api/decks?size=1000"), ownerToken)
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.size").value(100))
			.andExpect(jsonPath("$.content.length()").value(100))
			.andExpect(jsonPath("$.totalElements").value(120));

		// size 를 생략하면 기본값 20
		perform(get("/api/decks"), ownerToken).andExpect(jsonPath("$.size").value(20));
	}

	@Test
	void listQueryCountDoesNotGrowWithNumberOfDecks() throws Exception {
		Long first = createDeck(ownerToken, "deck-0");
		createCard(ownerToken, first, "card");
		long queriesWithOneDeck = countQueries(() -> perform(get("/api/decks"), ownerToken).andExpect(status().isOk()));

		for (int i = 1; i < 10; i++) {
			Long deckId = createDeck(ownerToken, "deck-" + i);
			createCard(ownerToken, deckId, "card");
			createCard(ownerToken, deckId, "card");
		}
		long queriesWithTenDecks = countQueries(
				() -> perform(get("/api/decks"), ownerToken).andExpect(status().isOk())
					.andExpect(jsonPath("$.content.length()").value(10)));

		// 덱이 1개든 10개든 쿼리 수가 같아야 한다 (덱마다 count 쿼리가 나가면 N+1).
		assertThat(queriesWithTenDecks).isEqualTo(queriesWithOneDeck);
		// 덱 페이지 조회 + 카드 수 집계. (첫 페이지가 다 차지 않으면 Spring Data 가 count 쿼리를 생략한다)
		assertThat(queriesWithTenDecks).isLessThanOrEqualTo(3);
	}

	// ===== 소유자 권한: 남의 덱은 404 =====

	@Test
	void otherMembersDeckReturns404AndIsNotChanged() throws Exception {
		Long deckId = createDeck(ownerToken, "mine");
		String otherToken = signupAndLogin(OTHER);

		perform(get("/api/decks/{deckId}", deckId), otherToken)
			.andExpect(status().isNotFound())
			.andExpect(jsonPath("$.code").value("DECK_NOT_FOUND"));
		perform(put("/api/decks/{deckId}", deckId), otherToken, """
				{"name": "hacked"}
				""")
			.andExpect(status().isNotFound())
			.andExpect(jsonPath("$.code").value("DECK_NOT_FOUND"));
		perform(delete("/api/decks/{deckId}", deckId), otherToken)
			.andExpect(status().isNotFound())
			.andExpect(jsonPath("$.code").value("DECK_NOT_FOUND"));

		// 존재하지 않는 덱도 똑같은 응답이다.
		perform(get("/api/decks/{deckId}", 999_999), otherToken)
			.andExpect(status().isNotFound())
			.andExpect(jsonPath("$.code").value("DECK_NOT_FOUND"));

		Deck deck = deckRepository.findById(deckId).orElseThrow();
		assertThat(deck.getName()).isEqualTo("mine");
	}

	// ===== 유효성 검증 / 인증 =====

	@Test
	void createDeckValidation() throws Exception {
		perform(post("/api/decks"), ownerToken, """
				{"name": "   "}
				""")
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("INVALID_INPUT"))
			.andExpect(jsonPath("$.errors[0].field").value("name"));

		perform(post("/api/decks"), ownerToken, """
				{"name": "%s", "description": "%s"}
				""".formatted("a".repeat(101), "b".repeat(501)))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.errors.length()").value(2));

		// 경계값은 통과
		perform(post("/api/decks"), ownerToken, """
				{"name": "%s", "description": "%s"}
				""".formatted("a".repeat(100), "b".repeat(500)))
			.andExpect(status().isCreated());
	}

	@Test
	void nonNumericDeckIdReturns400() throws Exception {
		perform(get("/api/decks/abc"), ownerToken)
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("INVALID_INPUT"))
			.andExpect(jsonPath("$.errors[0].field").value("deckId"));
	}

	@Test
	void requiresAuthentication() throws Exception {
		mockMvc.perform(get("/api/decks")).andExpect(status().isUnauthorized());
	}

	// ===== helpers =====

	private ResultActions perform(MockHttpServletRequestBuilder request, String token) throws Exception {
		return mockMvc.perform(request.header(HttpHeaders.AUTHORIZATION, bearer(token)));
	}

	private ResultActions perform(MockHttpServletRequestBuilder request, String token, String json)
			throws Exception {
		return mockMvc.perform(request.header(HttpHeaders.AUTHORIZATION, bearer(token))
			.contentType(MediaType.APPLICATION_JSON)
			.content(json));
	}

}
