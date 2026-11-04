package com.kotoba.backend.security;

import java.nio.charset.StandardCharsets;
import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * jwt.* 설정. 바인딩 시점(앱 기동 중)에 검증하므로 secret 이 없거나 짧으면 앱이 뜨지 않는다.
 */
@ConfigurationProperties("jwt")
public record JwtProperties(
		String secret,
		@DefaultValue("kotoba") String issuer,
		@DefaultValue("30m") Duration accessTokenTtl,
		@DefaultValue("14d") Duration refreshTokenTtl) {

	// HS256 은 256비트(32바이트) 이상의 키가 필요하다.
	static final int MIN_SECRET_BYTES = 32;

	public JwtProperties {
		if (secret == null || secret.isBlank()) {
			throw new IllegalStateException("JWT_SECRET 환경변수가 설정되지 않았습니다.");
		}
		if (secret.getBytes(StandardCharsets.UTF_8).length < MIN_SECRET_BYTES) {
			throw new IllegalStateException("JWT_SECRET 은 최소 " + MIN_SECRET_BYTES + "바이트 이상이어야 합니다.");
		}
	}

	@Override
	public String toString() {
		return "JwtProperties[secret=****, issuer=" + issuer + ", accessTokenTtl=" + accessTokenTtl
				+ ", refreshTokenTtl=" + refreshTokenTtl + "]";
	}

}
