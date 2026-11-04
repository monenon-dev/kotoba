package com.kotoba.backend.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;

import org.springframework.stereotype.Component;

/**
 * Refresh Token 원문 생성과 해시.
 *
 * Refresh Token 은 JWT 가 아니라 추측 불가능한 무작위 문자열이다. 어차피 DB 에서 조회해서 검증하므로
 * 서명이 필요 없고, 서버에서 언제든 폐기할 수 있다.
 * 해시는 BCrypt 가 아니라 SHA-256 을 쓴다. 원문이 256비트 무작위라 무차별 대입이 불가능하고,
 * 해시 값으로 DB 를 바로 조회해야 하기 때문이다 (BCrypt 는 salt 때문에 조회용으로 쓸 수 없다).
 */
@Component
public class RefreshTokenCodec {

	private static final int TOKEN_BYTES = 32;

	private final SecureRandom secureRandom = new SecureRandom();

	public String generate() {
		byte[] bytes = new byte[TOKEN_BYTES];
		secureRandom.nextBytes(bytes);
		return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
	}

	public String hash(String rawToken) {
		try {
			MessageDigest digest = MessageDigest.getInstance("SHA-256");
			return HexFormat.of().formatHex(digest.digest(rawToken.getBytes(StandardCharsets.UTF_8)));
		}
		catch (NoSuchAlgorithmException e) {
			throw new IllegalStateException("SHA-256 not available", e);
		}
	}

}
