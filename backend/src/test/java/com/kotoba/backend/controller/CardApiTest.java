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

import com.kotoba.backend.domain.Card;
import com.kotoba.backend.domain.Deck;
import com.kotoba.backend.support.IntegrationTestSupport;

class CardApiTest extends IntegrationTestSupport {

	private static final String OWNER = "owner@example.com";

	private static final String OTHER = "other@example.com";

	private String ownerToken;

	private Long deckId;

	@BeforeEach
	void setUp() throws Exception {
		ownerToken = signupAndLogin(OWNER);
		deckId = createDeck(ownerToken, "N3");
	}

	// ===== 정상 CRUD =====

	@Test
	void cardCrud() throws Exception {
		String created = perform(post("/api/decks/{deckId}/cards", deckId), ownerToken, """
				{"text": "勉強", "reading": "べんきょう", "meaning": "공부", "example": "毎日勉強します。"}
				""")
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.deckId").value(deckId))
			.andExpect(jsonPath("$.text").value("勉強"))
			.andExpect(jsonPath("$.reading").value("べんきょう"))
			.andExpect(jsonPath("$.meaning").value("공부"))
			.andExpect(jsonPath("$.example").value("毎日勉強します。"))
			.andReturn()
			.getResponse()
			.getContentAsString();
		Long cardId = ((Number) com.jayway.jsonpath.JsonPath.read(created, "$.id")).longValue();

		perform(get("/api/decks/{deckId}/cards/{cardId}", deckId, cardId), ownerToken)
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.text").value("勉強"));

		clock.advance(Duration.ofMinutes(1));
		// PUT 은 전체 교체: reading, example 을 생략하면 null 이 된다.
		perform(put("/api/decks/{deckId}/cards/{cardId}", deckId, cardId), ownerToken, """
				{"text": "勉強する", "meaning": "공부하다"}
				""")
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.text").value("勉強する"))
			.andExpect(jsonPath("$.meaning").value("공부하다"))
			.andExpect(jsonPath("$.reading").doesNotExist())
			.andExpect(jsonPath("$.example").doesNotExist());
		Card card = cardRepository.findById(cardId).orElseThrow();
		assertThat(card.getUpdatedAt()).isAfter(card.getCreatedAt());

		perform(delete("/api/decks/{deckId}/cards/{cardId}", deckId, cardId), ownerToken)
			.andExpect(status().isNoContent());
		perform(get("/api/decks/{deckId}/cards/{cardId}", deckId, cardId), ownerToken)
			.andExpect(status().isNotFound())
			.andExpect(jsonPath("$.code").value("CARD_NOT_FOUND"));
	}

	// ===== 목록과 페이지네이션 =====

	@Test
	void listIsPaginatedInInsertionOrder() throws Exception {
		for (int i = 0; i < 15; i++) {
			createCard(ownerToken, deckId, "card-" + i);
			clock.advance(Duration.ofSeconds(1));
		}

		perform(get("/api/decks/{deckId}/cards?page=0&size=10", deckId), ownerToken)
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.content.length()").value(10))
			.andExpect(jsonPath("$.content[0].text").value("card-0"))
			.andExpect(jsonPath("$.totalElements").value(15))
			.andExpect(jsonPath("$.totalPages").value(2))
			.andExpect(jsonPath("$.hasNext").value(true));

		perform(get("/api/decks/{deckId}/cards?page=1&size=10", deckId), ownerToken)
			.andExpect(jsonPath("$.content.length()").value(5))
			.andExpect(jsonPath("$.content[4].text").value("card-14"))
			.andExpect(jsonPath("$.hasNext").value(false));
	}

	@Test
	void pageSizeIsCappedAt100() throws Exception {
		Deck deck = deckRepository.findById(deckId).orElseThrow();
		List<Card> cards = new ArrayList<>();
		for (int i = 0; i < 105; i++) {
			cards.add(new Card(deck, "card-" + i, null, "meaning", null, clock.instant().plusSeconds(i)));
		}
		cardRepository.saveAll(cards);

		perform(get("/api/decks/{deckId}/cards?size=500", deckId), ownerToken)
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.size").value(100))
			.andExpect(jsonPath("$.content.length()").value(100))
			.andExpect(jsonPath("$.totalElements").value(105));
	}

	// ===== 소유자 권한: 남의 덱/카드는 404 =====

	@Test
	void otherMembersCardReturns404AndIsNotChanged() throws Exception {
		Long cardId = createCard(ownerToken, deckId, "勉強");
		String otherToken = signupAndLogin(OTHER);

		perform(get("/api/decks/{deckId}/cards/{cardId}", deckId, cardId), otherToken)
			.andExpect(status().isNotFound())
			.andExpect(jsonPath("$.code").value("CARD_NOT_FOUND"));
		perform(put("/api/decks/{deckId}/cards/{cardId}", deckId, cardId), otherToken, """
				{"text": "hacked", "meaning": "hacked"}
				""")
			.andExpect(status().isNotFound());
		perform(delete("/api/decks/{deckId}/cards/{cardId}", deckId, cardId), otherToken)
			.andExpect(status().isNotFound());

		// 남의 덱에 카드 추가, 남의 덱 카드 목록 → 덱이 없는 것과 같은 404
		perform(post("/api/decks/{deckId}/cards", deckId), otherToken, """
				{"text": "x", "meaning": "y"}
				""")
			.andExpect(status().isNotFound())
			.andExpect(jsonPath("$.code").value("DECK_NOT_FOUND"));
		perform(get("/api/decks/{deckId}/cards", deckId), otherToken)
			.andExpect(status().isNotFound())
			.andExpect(jsonPath("$.code").value("DECK_NOT_FOUND"));

		Card card = cardRepository.findById(cardId).orElseThrow();
		assertThat(card.getText()).isEqualTo("勉強");
		assertThat(cardRepository.countByDeckId(deckId)).isEqualTo(1);
	}

	@Test
	void cardAccessedThroughAnotherOwnDeckReturns404() throws Exception {
		Long cardId = createCard(ownerToken, deckId, "勉強");
		Long anotherDeckId = createDeck(ownerToken, "another");

		// 내 카드라도 URL 의 덱에 속하지 않으면 404
		perform(get("/api/decks/{deckId}/cards/{cardId}", anotherDeckId, cardId), ownerToken)
			.andExpect(status().isNotFound())
			.andExpect(jsonPath("$.code").value("CARD_NOT_FOUND"));
		perform(delete("/api/decks/{deckId}/cards/{cardId}", anotherDeckId, cardId), ownerToken)
			.andExpect(status().isNotFound());

		assertThat(cardRepository.existsById(cardId)).isTrue();
	}

	// ===== 유효성 검증 =====

	@Test
	void createCardValidation() throws Exception {
		// 필수값 누락
		perform(post("/api/decks/{deckId}/cards", deckId), ownerToken, """
				{"reading": "よみ"}
				""")
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("INVALID_INPUT"))
			.andExpect(jsonPath("$.errors.length()").value(2));

		// 길이 초과
		perform(post("/api/decks/{deckId}/cards", deckId), ownerToken, """
				{"text": "%s", "reading": "%s", "meaning": "%s", "example": "%s"}
				""".formatted("a".repeat(201), "b".repeat(201), "c".repeat(501), "d".repeat(1001)))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.errors.length()").value(4));

		perform(get("/api/decks/{deckId}/cards/abc", deckId), ownerToken)
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.errors[0].field").value("cardId"));

		assertThat(cardRepository.countByDeckId(deckId)).isZero();
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
