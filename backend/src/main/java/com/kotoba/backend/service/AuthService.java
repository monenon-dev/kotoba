package com.kotoba.backend.service;

import java.time.Clock;
import java.time.Instant;
import java.util.Locale;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.kotoba.backend.controller.dto.LoginRequest;
import com.kotoba.backend.controller.dto.MemberResponse;
import com.kotoba.backend.controller.dto.SignupRequest;
import com.kotoba.backend.controller.dto.TokenResponse;
import com.kotoba.backend.domain.Member;
import com.kotoba.backend.domain.RefreshToken;
import com.kotoba.backend.exception.BusinessException;
import com.kotoba.backend.exception.ErrorCode;
import com.kotoba.backend.repository.MemberRepository;
import com.kotoba.backend.repository.RefreshTokenRepository;
import com.kotoba.backend.security.JwtProperties;
import com.kotoba.backend.security.JwtTokenProvider;

@Service
public class AuthService {

	private static final Logger log = LoggerFactory.getLogger(AuthService.class);

	private final MemberRepository memberRepository;

	private final RefreshTokenRepository refreshTokenRepository;

	private final PasswordEncoder passwordEncoder;

	private final JwtTokenProvider jwtTokenProvider;

	private final RefreshTokenCodec refreshTokenCodec;

	private final JwtProperties jwtProperties;

	private final Clock clock;

	// 없는 이메일로 로그인해도 BCrypt 비교를 한 번 수행해서, 응답 시간으로 가입 여부를 추측하기 어렵게 한다.
	private final String dummyPasswordHash;

	public AuthService(MemberRepository memberRepository, RefreshTokenRepository refreshTokenRepository,
			PasswordEncoder passwordEncoder, JwtTokenProvider jwtTokenProvider, RefreshTokenCodec refreshTokenCodec,
			JwtProperties jwtProperties, Clock clock) {
		this.memberRepository = memberRepository;
		this.refreshTokenRepository = refreshTokenRepository;
		this.passwordEncoder = passwordEncoder;
		this.jwtTokenProvider = jwtTokenProvider;
		this.refreshTokenCodec = refreshTokenCodec;
		this.jwtProperties = jwtProperties;
		this.clock = clock;
		this.dummyPasswordHash = passwordEncoder.encode("dummy-password-for-timing");
	}

	@Transactional
	public MemberResponse signup(SignupRequest request) {
		String email = normalizeEmail(request.email());
		if (memberRepository.existsByEmail(email)) {
			throw new BusinessException(ErrorCode.EMAIL_ALREADY_EXISTS);
		}
		Member member = new Member(email, passwordEncoder.encode(request.password()), clock.instant());
		try {
			memberRepository.saveAndFlush(member);
		}
		catch (DataIntegrityViolationException e) {
			// 동시에 같은 이메일로 가입한 경우 unique 제약이 최종 방어선이 된다.
			throw new BusinessException(ErrorCode.EMAIL_ALREADY_EXISTS);
		}
		return MemberResponse.from(member);
	}

	@Transactional
	public TokenResponse login(LoginRequest request) {
		Member member = memberRepository.findByEmail(normalizeEmail(request.email())).orElse(null);
		String hash = member != null ? member.getPasswordHash() : dummyPasswordHash;
		boolean matches = passwordEncoder.matches(request.password(), hash);
		if (member == null || !matches) {
			throw new BusinessException(ErrorCode.INVALID_CREDENTIALS);
		}
		return issueTokens(member);
	}

	/**
	 * Refresh Token rotation. 사용된 토큰은 폐기하고 새 토큰 쌍을 발급한다.
	 * 이미 폐기된 토큰이 다시 들어오면 탈취로 보고 그 회원의 모든 Refresh Token 을 폐기한다.
	 * 이 폐기는 예외를 던진 뒤에도 유지되어야 하므로 BusinessException 에서는 롤백하지 않는다.
	 */
	@Transactional(noRollbackFor = BusinessException.class)
	public TokenResponse refresh(String rawRefreshToken) {
		Instant now = clock.instant();
		RefreshToken token = refreshTokenRepository.findByTokenHash(refreshTokenCodec.hash(rawRefreshToken))
			.orElseThrow(() -> new BusinessException(ErrorCode.INVALID_REFRESH_TOKEN));

		if (token.isRevoked()) {
			Member member = token.getMember();
			int revoked = refreshTokenRepository.revokeAllByMember(member, now);
			log.warn("Refresh token reuse detected. memberId={}, revokedTokens={}", member.getId(), revoked);
			throw new BusinessException(ErrorCode.INVALID_REFRESH_TOKEN);
		}
		if (token.isExpired(now)) {
			throw new BusinessException(ErrorCode.INVALID_REFRESH_TOKEN);
		}

		token.revoke(now);
		return issueTokens(token.getMember());
	}

	/**
	 * 본인 소유의 Refresh Token 을 폐기한다. 이미 폐기됐거나 없는 토큰이어도 같은 결과(성공)로 처리한다.
	 * Access Token 은 상태가 없어서 만료 시각까지는 유효하다 (그래서 수명을 짧게 둔다).
	 */
	@Transactional
	public void logout(Long memberId, String rawRefreshToken) {
		refreshTokenRepository.findByTokenHash(refreshTokenCodec.hash(rawRefreshToken))
			.filter(token -> token.getMember().getId().equals(memberId))
			.ifPresent(token -> token.revoke(clock.instant()));
	}

	private TokenResponse issueTokens(Member member) {
		Instant now = clock.instant();
		String rawRefreshToken = refreshTokenCodec.generate();
		refreshTokenRepository.save(new RefreshToken(member, refreshTokenCodec.hash(rawRefreshToken), now,
				now.plus(jwtProperties.refreshTokenTtl())));
		return TokenResponse.bearer(jwtTokenProvider.createAccessToken(member),
				jwtTokenProvider.accessTokenTtlSeconds(), rawRefreshToken, jwtProperties.refreshTokenTtl().toSeconds());
	}

	private String normalizeEmail(String email) {
		return email.trim().toLowerCase(Locale.ROOT);
	}

}
