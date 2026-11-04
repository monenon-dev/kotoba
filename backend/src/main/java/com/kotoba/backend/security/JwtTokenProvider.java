package com.kotoba.backend.security;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.util.Date;

import javax.crypto.SecretKey;

import org.springframework.stereotype.Component;

import com.kotoba.backend.domain.Member;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.JwtParser;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;

/**
 * Access Token(JWT) 발급과 검증.
 * 토큰 본문은 누구나 base64 디코딩해서 볼 수 있으므로 회원 id 와 role 만 넣고 이메일 등은 넣지 않는다.
 */
@Component
public class JwtTokenProvider {

	private static final String ROLE_CLAIM = "role";

	private final JwtProperties properties;

	private final Clock clock;

	private final SecretKey key;

	private final JwtParser parser;

	public JwtTokenProvider(JwtProperties properties, Clock clock) {
		this.properties = properties;
		this.clock = clock;
		this.key = Keys.hmacShaKeyFor(properties.secret().getBytes(StandardCharsets.UTF_8));
		this.parser = Jwts.parser()
			.verifyWith(key)
			.requireIssuer(properties.issuer())
			// 만료 판단도 주입된 Clock 을 따르게 해서 테스트에서 시간을 조절할 수 있게 한다.
			.clock(() -> Date.from(clock.instant()))
			.build();
	}

	public String createAccessToken(Member member) {
		Instant now = clock.instant();
		return Jwts.builder()
			.issuer(properties.issuer())
			.subject(String.valueOf(member.getId()))
			.claim(ROLE_CLAIM, member.getRole().name())
			.issuedAt(Date.from(now))
			.expiration(Date.from(now.plus(properties.accessTokenTtl())))
			.signWith(key)
			.compact();
	}

	/**
	 * 서명·만료·발급자를 검증하고 principal 을 돌려준다.
	 * @throws JwtException 토큰이 유효하지 않을 때
	 */
	public AuthenticatedMember parse(String token) {
		Claims claims = parser.parseSignedClaims(token).getPayload();
		Long memberId;
		try {
			memberId = Long.valueOf(claims.getSubject());
		}
		catch (NumberFormatException e) {
			throw new JwtException("Invalid subject");
		}
		return new AuthenticatedMember(memberId, claims.get(ROLE_CLAIM, String.class));
	}

	public long accessTokenTtlSeconds() {
		return properties.accessTokenTtl().toSeconds();
	}

}
