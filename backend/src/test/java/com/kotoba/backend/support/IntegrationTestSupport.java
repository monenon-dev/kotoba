package com.kotoba.backend.support;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;

import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;

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
import com.kotoba.backend.repository.ReviewLogRepository;
import com.kotoba.backend.repository.ReviewStateRepository;

import jakarta.persistence.EntityManagerFactory;

/**
 * 통합 테스트 공통 설정. 모든 통합 테스트가 같은 설정을 쓰면 Spring 테스트 컨텍스트(와 H2 DB)가 하나만 만들어진다.
 * 테스트 클래스에 @Transactional 을 붙이지 않고 매번 테이블을 비운다. 서비스의 실제 트랜잭션 경계를 그대로 검증하기 위해서다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles(resolver = TestDatabaseProfileResolver.class)
@Import(TestClockConfig.class)
public abstract class IntegrationTestSupport {

	protected static final String PASSWORD = "password1234";

	/** application.yml 의 app.study.zone 기본값과 같다. */
	protected static final ZoneId STUDY_ZONE = ZoneId.of("Asia/Seoul");

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

	@Autowired
	protected ReviewStateRepository reviewStateRepository;

	@Autowired
	protected ReviewLogRepository reviewLogRepository;

	@Autowired
	private EntityManagerFactory entityManagerFactory;

	@BeforeEach
	void cleanDatabaseAndResetClock() {
		// 외래키 방향의 역순으로 지운다.
		reviewLogRepository.deleteAllInBatch();
		reviewStateRepository.deleteAllInBatch();
		cardRepository.deleteAllInBatch();
		deckRepository.deleteAllInBatch();
		refreshTokenRepository.deleteAllInBatch();
		memberRepository.deleteAllInBatch();
		// Linux JDK 는 나노초까지 주지만 PostgreSQL 은 마이크로초로 반올림한다. 밀리초로 잘라 DB 왕복 후 값이 바뀌지 않게 한다.
		clock.setNow(Instant.now().truncatedTo(ChronoUnit.MILLIS));
	}

	/** 회원가입 후 로그인해서 Access Token 을 돌려준다. */
	protected String signupAndLogin(String email) throws Exception {
		mockMvc.perform(post("/api/auth/signup").contentType(MediaType.APPLICATION_JSON).content(credentials(email)))
			.andExpect(status().isCreated());
		return login(email);
	}

	/**
	 * 로그인해서 현재 Clock 기준의 새 Access Token 을 돌려준다.
	 * Access Token 은 30분짜리라, Clock 을 30분 넘게 옮긴 테스트는 다시 로그인해야 한다.
	 */
	protected String login(String email) throws Exception {
		String body = credentials(email);
		String response = mockMvc
			.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content(body))
			.andExpect(status().isOk())
			.andReturn()
			.getResponse()
			.getContentAsString();
		return JsonPath.read(response, "$.accessToken");
	}

	private static String credentials(String email) {
		return """
				{"email": "%s", "password": "%s"}
				""".formatted(email, PASSWORD);
	}

	protected Long memberIdOf(String email) {
		return memberRepository.findByEmail(email).orElseThrow().getId();
	}

	/** 기준 시간대(Asia/Seoul)의 시각으로 Clock 을 맞춘다. 예: setSeoulTime(2026, 11, 4, 23, 59, 0) */
	protected void setSeoulTime(int year, int month, int day, int hour, int minute, int second) {
		clock.setNow(LocalDateTime.of(year, month, day, hour, minute, second).atZone(STUDY_ZONE).toInstant());
	}

	/** action 실행 중 Hibernate 가 준비한 SQL 문 수. N+1 이나 데이터 양에 비례하는 쿼리를 잡는 데 쓴다. */
	protected long countQueries(ThrowingRunnable action) throws Exception {
		Statistics statistics = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
		statistics.setStatisticsEnabled(true);
		statistics.clear();
		try {
			action.run();
			return statistics.getPrepareStatementCount();
		}
		finally {
			statistics.setStatisticsEnabled(false);
		}
	}

	@FunctionalInterface
	protected interface ThrowingRunnable {

		void run() throws Exception;

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
