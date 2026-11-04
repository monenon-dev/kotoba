package com.kotoba.backend.domain;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

/**
 * 발급된 Refresh Token. 원문이 아니라 SHA-256 해시만 저장한다.
 * 재발급(rotation) 시 기존 토큰은 삭제하지 않고 revokedAt 을 기록해서,
 * 이미 사용된 토큰이 다시 들어오면 탈취로 보고 감지할 수 있게 한다.
 */
@Entity
@Table(name = "refresh_tokens",
		uniqueConstraints = @UniqueConstraint(name = "uk_refresh_tokens_token_hash", columnNames = "token_hash"),
		indexes = @Index(name = "idx_refresh_tokens_member_id", columnList = "member_id"))
public class RefreshToken {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "member_id", nullable = false)
	private Member member;

	@Column(name = "token_hash", nullable = false, length = 64)
	private String tokenHash;

	@Column(nullable = false)
	private Instant expiresAt;

	@Column(nullable = false)
	private Instant createdAt;

	private Instant revokedAt;

	protected RefreshToken() {
	}

	public RefreshToken(Member member, String tokenHash, Instant createdAt, Instant expiresAt) {
		this.member = member;
		this.tokenHash = tokenHash;
		this.createdAt = createdAt;
		this.expiresAt = expiresAt;
	}

	public boolean isExpired(Instant now) {
		return !now.isBefore(expiresAt);
	}

	public boolean isRevoked() {
		return revokedAt != null;
	}

	public void revoke(Instant now) {
		if (revokedAt == null) {
			revokedAt = now;
		}
	}

	public Long getId() {
		return id;
	}

	public Member getMember() {
		return member;
	}

	public Instant getExpiresAt() {
		return expiresAt;
	}

	@Override
	public String toString() {
		return "RefreshToken{id=" + id + ", expiresAt=" + expiresAt + ", revoked=" + isRevoked() + "}";
	}

}
