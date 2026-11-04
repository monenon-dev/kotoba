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
 * 덱에 속한 학습 카드. 소유자는 deck → member 로 판단하며 member_id 를 따로 두지 않는다.
 * Deck 쪽에 cards 컬렉션을 두지 않는 단방향 관계다.
 */
@Entity
@Table(name = "cards", indexes = @Index(name = "idx_cards_deck_id", columnList = "deck_id"))
public class Card {

	public static final int TEXT_MAX_LENGTH = 200;

	public static final int READING_MAX_LENGTH = 200;

	public static final int MEANING_MAX_LENGTH = 500;

	public static final int EXAMPLE_MAX_LENGTH = 1000;

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "deck_id", nullable = false)
	private Deck deck;

	// 표현 (예: 勉強)
	@Column(nullable = false, length = TEXT_MAX_LENGTH)
	private String text;

	// 읽기 (예: べんきょう)
	@Column(length = READING_MAX_LENGTH)
	private String reading;

	// 뜻 (예: 공부)
	@Column(nullable = false, length = MEANING_MAX_LENGTH)
	private String meaning;

	@Column(length = EXAMPLE_MAX_LENGTH)
	private String example;

	@Column(nullable = false)
	private Instant createdAt;

	@Column(nullable = false)
	private Instant updatedAt;

	protected Card() {
	}

	public Card(Deck deck, String text, String reading, String meaning, String example, Instant now) {
		this.deck = deck;
		this.text = text;
		this.reading = reading;
		this.meaning = meaning;
		this.example = example;
		this.createdAt = now;
		this.updatedAt = now;
	}

	public void update(String text, String reading, String meaning, String example, Instant now) {
		this.text = text;
		this.reading = reading;
		this.meaning = meaning;
		this.example = example;
		this.updatedAt = now;
	}

	public Long getId() {
		return id;
	}

	public Deck getDeck() {
		return deck;
	}

	public String getText() {
		return text;
	}

	public String getReading() {
		return reading;
	}

	public String getMeaning() {
		return meaning;
	}

	public String getExample() {
		return example;
	}

	public Instant getCreatedAt() {
		return createdAt;
	}

	public Instant getUpdatedAt() {
		return updatedAt;
	}

	@Override
	public String toString() {
		return "Card{id=" + id + "}";
	}

}
