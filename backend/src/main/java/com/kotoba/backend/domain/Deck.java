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

/**
 * 회원이 소유한 단어장.
 * Member 쪽에 decks 컬렉션을 두지 않는 단방향 관계다. 목록은 항상 리포지토리 페이지 쿼리로 가져온다.
 */
@Entity
@Table(name = "decks", indexes = @Index(name = "idx_decks_member_id", columnList = "member_id"))
public class Deck {

	public static final int NAME_MAX_LENGTH = 100;

	public static final int DESCRIPTION_MAX_LENGTH = 500;

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "member_id", nullable = false)
	private Member member;

	@Column(nullable = false, length = NAME_MAX_LENGTH)
	private String name;

	@Column(length = DESCRIPTION_MAX_LENGTH)
	private String description;

	@Column(nullable = false)
	private Instant createdAt;

	@Column(nullable = false)
	private Instant updatedAt;

	protected Deck() {
	}

	public Deck(Member member, String name, String description, Instant now) {
		this.member = member;
		this.name = name;
		this.description = description;
		this.createdAt = now;
		this.updatedAt = now;
	}

	public void update(String name, String description, Instant now) {
		this.name = name;
		this.description = description;
		this.updatedAt = now;
	}

	public Long getId() {
		return id;
	}

	public Member getMember() {
		return member;
	}

	public String getName() {
		return name;
	}

	public String getDescription() {
		return description;
	}

	public Instant getCreatedAt() {
		return createdAt;
	}

	public Instant getUpdatedAt() {
		return updatedAt;
	}

	@Override
	public String toString() {
		return "Deck{id=" + id + "}";
	}

}
