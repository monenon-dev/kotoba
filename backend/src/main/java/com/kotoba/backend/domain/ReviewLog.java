package com.kotoba.backend.domain;

import java.time.Instant;
import java.time.LocalDate;

import com.kotoba.backend.srs.Rating;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

/**
 * 복습 한 번의 기록. 통계는 모두 이 테이블에서 집계한다.
 *
 * card_id, deck_id 에는 외래키를 두지 않는다. 카드나 덱을 지워도 "그날 공부했다"는 기록과
 * 연속 학습일, 오늘 시작한 신규 카드 수가 바뀌지 않게 하기 위해서다.
 * review_date 는 기준 시간대의 날짜를 Java 에서 계산해 저장한다. 날짜 변환 SQL 이 DB 마다 달라서다.
 */
@Entity
@Table(name = "review_logs",
		indexes = @Index(name = "idx_review_logs_member_date", columnList = "member_id, review_date"))
public class ReviewLog {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "member_id", nullable = false)
	private Member member;

	@Column(name = "deck_id", nullable = false)
	private Long deckId;

	@Column(name = "card_id", nullable = false)
	private Long cardId;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 10)
	private Rating rating;

	// 그 카드의 첫 복습(복습 상태 행이 없던 신규 카드)이면 true. 하루 신규 한도 계산에 쓴다.
	@Column(nullable = false)
	private boolean firstReview;

	// 신규 카드면 null
	private Integer previousIntervalDays;

	@Column(nullable = false)
	private int newIntervalDays;

	@Column(nullable = false)
	private Instant reviewedAt;

	@Column(name = "review_date", nullable = false)
	private LocalDate reviewDate;

	protected ReviewLog() {
	}

	public ReviewLog(Member member, Long deckId, Long cardId, Rating rating, boolean firstReview,
			Integer previousIntervalDays, int newIntervalDays, Instant reviewedAt, LocalDate reviewDate) {
		this.member = member;
		this.deckId = deckId;
		this.cardId = cardId;
		this.rating = rating;
		this.firstReview = firstReview;
		this.previousIntervalDays = previousIntervalDays;
		this.newIntervalDays = newIntervalDays;
		this.reviewedAt = reviewedAt;
		this.reviewDate = reviewDate;
	}

	public Long getId() {
		return id;
	}

	public Long getCardId() {
		return cardId;
	}

	public Rating getRating() {
		return rating;
	}

	public boolean isFirstReview() {
		return firstReview;
	}

	public Integer getPreviousIntervalDays() {
		return previousIntervalDays;
	}

	public int getNewIntervalDays() {
		return newIntervalDays;
	}

	public Instant getReviewedAt() {
		return reviewedAt;
	}

	public LocalDate getReviewDate() {
		return reviewDate;
	}

	@Override
	public String toString() {
		return "ReviewLog{id=" + id + ", rating=" + rating + ", reviewDate=" + reviewDate + "}";
	}

}
