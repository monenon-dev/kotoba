package com.kotoba.backend.support;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;

import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import com.jayway.jsonpath.JsonPath;
import com.kotoba.backend.repository.CardRepository;
import com.kotoba.backend.repository.DeckRepository;
import com.kotoba.backend.repository.MemberRepository;
import com.kotoba.backend.repository.RefreshTokenRepository;

/**
 * 통합 테스트 공통 설정. 모든 통합 테스트가 같은 설정을 쓰면 Spring 테스트 컨텍스트(와 H2 DB)가 하나만 만들어진다.
 * 테스트 클래스에 @Transactional 을 붙이지 않고 매번 테이블을 비운다. 서비스의 실제 트랜잭션 경계를 그대로 검증하기 위해서다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("dev")
@Import(TestClockConfig.class)
public abstract class IntegrationTestSupport {

	protected static final String PASSWORD = "password1234";

	@Autowired
	protected MockMvc mockMvc;

	@Autowired
	protected MutableClock clock;

	@Autowired
	protected MemberRepository memberRepository;

	@Autowired
	protected RefreshTokenRepository refreshTokenRepository;

	@Autowired
	protected DeckRepository deckRepository;

	@Autowired
	protected CardRepository cardRepository;

	@BeforeEach
	void cleanDatabaseAndResetClock() {
		// 외래키 방향의 역순으로 지운다.
		cardRepository.deleteAllInBatch();
		deckRepository.deleteAllInBatch();
		refreshTokenRepository.deleteAllInBatch();
		memberRepository.deleteAllInBatch();
		clock.setNow(Instant.now());
	}

	/** 회원가입 후 로그인해서 Access Token 을 돌려준다. */
	protected String signupAndLogin(String email) throws Exception {
		String body = """
				{"email": "%s", "password": "%s"}
				""".formatted(email, PASSWORD);
		mockMvc.perform(post("/api/auth/signup").contentType(MediaType.APPLICATION_JSON).content(body))
			.andExpect(status().isCreated());
		String response = mockMvc
			.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content(body))
			.andExpect(status().isOk())
			.andReturn()
			.getResponse()
			.getContentAsString();
		return JsonPath.read(response, "$.accessToken");
	}

	protected Long memberIdOf(String email) {
		return memberRepository.findByEmail(email).orElseThrow().getId();
	}

	protected static String bearer(String accessToken) {
		return "Bearer " + accessToken;
	}

	/** API 로 덱을 만들고 id 를 돌려준다. */
	protected Long createDeck(String accessToken, String name) throws Exception {
		String response = mockMvc
			.perform(post("/api/decks").header(HttpHeaders.AUTHORIZATION, bearer(accessToken))
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{"name": "%s", "description": "desc"}
						""".formatted(name)))
			.andExpect(status().isCreated())
			.andReturn()
			.getResponse()
			.getContentAsString();
		return ((Number) JsonPath.read(response, "$.id")).longValue();
	}

	/** API 로 카드를 만들고 id 를 돌려준다. */
	protected Long createCard(String accessToken, Long deckId, String text) throws Exception {
		String response = mockMvc
			.perform(post("/api/decks/{deckId}/cards", deckId).header(HttpHeaders.AUTHORIZATION, bearer(accessToken))
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{"text": "%s", "reading": "よみ", "meaning": "뜻", "example": "例文"}
						""".formatted(text)))
			.andExpect(status().isCreated())
			.andReturn()
			.getResponse()
			.getContentAsString();
		return ((Number) JsonPath.read(response, "$.id")).longValue();
	}

}
