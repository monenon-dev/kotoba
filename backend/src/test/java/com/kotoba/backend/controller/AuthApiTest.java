package com.kotoba.backend.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;

import javax.crypto.SecretKey;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import com.jayway.jsonpath.JsonPath;
import com.kotoba.backend.repository.MemberRepository;
import com.kotoba.backend.repository.RefreshTokenRepository;
import com.kotoba.backend.service.RefreshTokenCodec;
import com.kotoba.backend.support.MutableClock;
import com.kotoba.backend.support.TestClockConfig;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("dev")
@Import(TestClockConfig.class)
class AuthApiTest {

	private static final String EMAIL = "learner@example.com";

	private static final String PASSWORD = "password1234";

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private MutableClock clock;

	@Autowired
	private MemberRepository memberRepository;

	@Autowired
	private RefreshTokenRepository refreshTokenRepository;

	@Autowired
	private RefreshTokenCodec refreshTokenCodec;

	@BeforeEach
	void setUp() {
		refreshTokenRepository.deleteAll();
		memberRepository.deleteAll();
		clock.setNow(Instant.now());
	}

	// ===== 회원가입 =====

	@Test
	void signupSucceeds() throws Exception {
		signup(EMAIL, PASSWORD)
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.id").isNumber())
			.andExpect(jsonPath("$.email").value(EMAIL))
			.andExpect(jsonPath("$.role").value("USER"))
			.andExpect(jsonPath("$.password").doesNotExist())
			.andExpect(jsonPath("$.passwordHash").doesNotExist());

		// 비밀번호는 원문이 아니라 BCrypt 해시로 저장된다.
		String stored = memberRepository.findByEmail(EMAIL).orElseThrow().getPasswordHash();
		assertThat(stored).isNotEqualTo(PASSWORD).startsWith("$2");
	}

	@Test
	void signupFailsWhenEmailAlreadyExists() throws Exception {
		signup(EMAIL, PASSWORD).andExpect(status().isCreated());

		// 대소문자만 다른 이메일도 중복으로 본다.
		signup("Learner@Example.com", "another-password")
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("EMAIL_ALREADY_EXISTS"));
	}

	@Test
	void signupFailsWithInvalidInput() throws Exception {
		signup("not-an-email", "short")
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("INVALID_INPUT"))
			.andExpect(jsonPath("$.errors.length()").value(2))
			// 입력값(특히 비밀번호)은 에러 응답에 다시 노출하지 않는다.
			.andExpect(jsonPath("$.errors[*].rejectedValue").doesNotExist());
	}

	// ===== 로그인 =====

	@Test
	void loginSucceeds() throws Exception {
		signup(EMAIL, PASSWORD);

		login(EMAIL, PASSWORD)
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.tokenType").value("Bearer"))
			.andExpect(jsonPath("$.accessToken").isString())
			.andExpect(jsonPath("$.refreshToken").isString())
			.andExpect(jsonPath("$.accessTokenExpiresIn").value(1800));
	}

	@Test
	void loginFailsWithWrongPasswordOrUnknownEmail() throws Exception {
		signup(EMAIL, PASSWORD);

		login(EMAIL, "wrong-password")
			.andExpect(status().isUnauthorized())
			.andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"));

		// 없는 이메일도 같은 응답이라 가입 여부를 알 수 없다.
		login("nobody@example.com", PASSWORD)
			.andExpect(status().isUnauthorized())
			.andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"));
	}

	// ===== /api/users/me =====

	@Test
	void meWithoutTokenReturns401() throws Exception {
		mockMvc.perform(get("/api/users/me"))
			.andExpect(status().isUnauthorized())
			.andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
	}

	@Test
	void meWithValidTokenSucceeds() throws Exception {
		Tokens tokens = signupAndLogin();

		me(tokens.accessToken())
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.email").value(EMAIL));
	}

	@Test
	void meWithForgedTokenReturns401() throws Exception {
		Tokens tokens = signupAndLogin();
		String memberId = String.valueOf(memberRepository.findByEmail(EMAIL).orElseThrow().getId());

		// 1) 같은 내용이지만 다른 키로 서명한 토큰
		SecretKey otherKey = Keys.hmacShaKeyFor("another-secret-key-another-secret-key!!".getBytes(StandardCharsets.UTF_8));
		String forged = Jwts.builder()
			.issuer("kotoba")
			.subject(memberId)
			.claim("role", "USER")
			.issuedAt(Date.from(clock.instant()))
			.expiration(Date.from(clock.instant().plus(Duration.ofMinutes(30))))
			.signWith(otherKey)
			.compact();
		me(forged).andExpect(status().isUnauthorized());

		// 2) 서명은 그대로 두고 payload(sub)만 바꾼 토큰
		String[] parts = tokens.accessToken().split("\\.");
		String payload = new String(Base64.getUrlDecoder().decode(parts[1]), StandardCharsets.UTF_8)
			.replace("\"sub\":\"" + memberId + "\"", "\"sub\":\"999999\"");
		String tampered = parts[0] + "." + Base64.getUrlEncoder().withoutPadding()
			.encodeToString(payload.getBytes(StandardCharsets.UTF_8)) + "." + parts[2];
		assertThat(tampered).isNotEqualTo(tokens.accessToken());
		me(tampered).andExpect(status().isUnauthorized());
	}

	@Test
	void meWithExpiredTokenReturns401() throws Exception {
		Tokens tokens = signupAndLogin();

		clock.advance(Duration.ofMinutes(31));
		me(tokens.accessToken()).andExpect(status().isUnauthorized());
	}

	// ===== refresh =====

	@Test
	void refreshIssuesNewTokenPair() throws Exception {
		Tokens first = signupAndLogin();

		Tokens second = toTokens(refresh(first.refreshToken())
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.refreshToken").value(not(first.refreshToken()))));

		me(second.accessToken()).andExpect(status().isOk());
	}

	@Test
	void refreshTokenIsStoredAsHash() throws Exception {
		Tokens tokens = signupAndLogin();

		assertThat(refreshTokenRepository.findByTokenHash(tokens.refreshToken())).isEmpty();
		assertThat(refreshTokenRepository.findByTokenHash(refreshTokenCodec.hash(tokens.refreshToken()))).isPresent();
	}

	@Test
	void refreshFailsWhenExpired() throws Exception {
		Tokens tokens = signupAndLogin();

		clock.advance(Duration.ofDays(14).plusSeconds(1));

		refresh(tokens.refreshToken())
			.andExpect(status().isUnauthorized())
			.andExpect(jsonPath("$.code").value("INVALID_REFRESH_TOKEN"));
	}

	@Test
	void refreshFailsOnReuseAndRevokesAllTokens() throws Exception {
		Tokens first = signupAndLogin();
		Tokens second = toTokens(refresh(first.refreshToken()).andExpect(status().isOk()));

		// 이미 사용(교체)된 토큰을 다시 쓰면 실패한다.
		refresh(first.refreshToken())
			.andExpect(status().isUnauthorized())
			.andExpect(jsonPath("$.code").value("INVALID_REFRESH_TOKEN"));

		// 탈취로 간주해 정상 사용자 쪽 최신 토큰까지 폐기됐는지 확인한다.
		refresh(second.refreshToken()).andExpect(status().isUnauthorized());
	}

	@Test
	void refreshFailsWithUnknownToken() throws Exception {
		refresh("unknown-token")
			.andExpect(status().isUnauthorized())
			.andExpect(jsonPath("$.code").value("INVALID_REFRESH_TOKEN"));
	}

	// ===== logout =====

	@Test
	void logoutRevokesRefreshToken() throws Exception {
		Tokens tokens = signupAndLogin();

		logout(tokens.accessToken(), tokens.refreshToken()).andExpect(status().isNoContent());

		refresh(tokens.refreshToken()).andExpect(status().isUnauthorized());
	}

	@Test
	void logoutRequiresAccessToken() throws Exception {
		Tokens tokens = signupAndLogin();

		mockMvc.perform(post("/api/auth/logout").contentType(MediaType.APPLICATION_JSON)
			.content(refreshBody(tokens.refreshToken()))).andExpect(status().isUnauthorized());
	}

	// ===== helpers =====

	private ResultActions signup(String email, String password) throws Exception {
		return mockMvc.perform(post("/api/auth/signup").contentType(MediaType.APPLICATION_JSON)
			.content(credentialsBody(email, password)));
	}

	private ResultActions login(String email, String password) throws Exception {
		return mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
			.content(credentialsBody(email, password)));
	}

	private ResultActions refresh(String refreshToken) throws Exception {
		return mockMvc.perform(post("/api/auth/refresh").contentType(MediaType.APPLICATION_JSON)
			.content(refreshBody(refreshToken)));
	}

	private ResultActions logout(String accessToken, String refreshToken) throws Exception {
		return mockMvc.perform(post("/api/auth/logout").header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
			.contentType(MediaType.APPLICATION_JSON)
			.content(refreshBody(refreshToken)));
	}

	private ResultActions me(String accessToken) throws Exception {
		return mockMvc.perform(get("/api/users/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken));
	}

	private Tokens signupAndLogin() throws Exception {
		signup(EMAIL, PASSWORD).andExpect(status().isCreated());
		return toTokens(login(EMAIL, PASSWORD).andExpect(status().isOk()));
	}

	private Tokens toTokens(ResultActions result) throws Exception {
		String body = result.andReturn().getResponse().getContentAsString();
		return new Tokens(JsonPath.read(body, "$.accessToken"), JsonPath.read(body, "$.refreshToken"));
	}

	private static String credentialsBody(String email, String password) {
		return """
				{"email": "%s", "password": "%s"}
				""".formatted(email, password);
	}

	private static String refreshBody(String refreshToken) {
		return """
				{"refreshToken": "%s"}
				""".formatted(refreshToken);
	}

	private record Tokens(String accessToken, String refreshToken) {
	}

}
