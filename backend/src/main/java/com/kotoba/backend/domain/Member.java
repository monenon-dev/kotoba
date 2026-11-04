package com.kotoba.backend.domain;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

// "user" 는 PostgreSQL 예약어라서 테이블명을 members 로 둔다.
@Entity
@Table(name = "members", uniqueConstraints = @UniqueConstraint(name = "uk_members_email", columnNames = "email"))
public class Member {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(nullable = false, length = 255)
	private String email;

	// BCrypt 해시만 저장한다. 원문 비밀번호는 어디에도 남기지 않는다.
	@Column(nullable = false, length = 100)
	private String passwordHash;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	private MemberRole role;

	@Column(nullable = false)
	private Instant createdAt;

	protected Member() {
	}

	public Member(String email, String passwordHash, Instant createdAt) {
		this.email = email;
		this.passwordHash = passwordHash;
		this.role = MemberRole.USER;
		this.createdAt = createdAt;
	}

	public Long getId() {
		return id;
	}

	public String getEmail() {
		return email;
	}

	public String getPasswordHash() {
		return passwordHash;
	}

	public MemberRole getRole() {
		return role;
	}

	public Instant getCreatedAt() {
		return createdAt;
	}

	// 엔티티가 로그에 찍혀도 비밀번호 해시가 노출되지 않도록 직접 정의한다.
	@Override
	public String toString() {
		return "Member{id=" + id + ", role=" + role + "}";
	}

}
