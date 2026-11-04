package com.kotoba.backend.domain;

import java.time.Instant;

import com.kotoba.backend.srs.ReviewSchedule;
import com.kotoba.backend.srs.ScheduleResult;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.persistence.Version;

/**
 * 카드 한 장의 복습 상태. 신규 카드는 이 행이 없고, 첫 복습 때 만들어진다.
 *
 * deckId, memberId 는 card → deck → member 로 알 수 있는 값을 비정규화한 것이다.
 * 가장 자주 쓰는 기한 조회(deck_id/member_id + due_at)를 조인 없이 인덱스로 처리하기 위해서다.
 * 카드를 다른 덱으로 옮기는 기능이 생기면 함께 갱신해야 한다.
 */
@Entity
@Table(name = "review_states",
		uniqueConstraints = @UniqueConstraint(name = "uk_review_states_card_id", columnNames = "card_id"),
		indexes = { @Index(name = "idx_review_states_deck_due", columnList = "deck_id, due_at"),
				@Index(name = "idx_review_states_member_due", columnList = "member_id, due_at") })
public class ReviewState {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@OneToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "card_id", nullable = false)
	private Card card;

	@Column(name = "deck_id", nullable = false)
	private Long deckId;

	@Column(name = "member_id", nullable = false)
	private Long memberId;

	@Column(name = "due_at", nullable = false)
	private Instant dueAt;

	@Column(nullable = false)
	private int intervalDays;

	@Column(nullable = false)
	private int easePermille;

	@Column(nullable = false)
	private int repetitions;

	@Column(nullable = false)
	private int lapses;

	@Column(nullable = false)
	private Instant lastReviewedAt;

	// 같은 카드를 동시에 복습하면 나중에 커밋하려는 쪽의 UPDATE 가 0행이 되어 충돌로 감지된다.
	@Version
	@Column(nullable = false)
	private Long version;

	@Column(nullable = false)
	private Instant createdAt;

	@Column(nullable = false)
	private Instant updatedAt;

	protected ReviewState() {
	}

	public ReviewState(Card card, Long deckId, Long memberId, ScheduleResult first) {
		this.card = card;
		this.deckId = deckId;
		this.memberId = memberId;
		this.createdAt = first.reviewedAt();
		apply(first);
	}

	public void apply(ScheduleResult result) {
		ReviewSchedule schedule = result.schedule();
		this.intervalDays = schedule.intervalDays();
		this.easePermille = schedule.easePermille();
		this.repetitions = schedule.repetitions();
		this.lapses = schedule.lapses();
		this.dueAt = result.dueAt();
		this.lastReviewedAt = result.reviewedAt();
		this.updatedAt = result.reviewedAt();
	}

	public ReviewSchedule schedule() {
		return new ReviewSchedule(repetitions, intervalDays, easePermille, lapses);
	}

	public Long getId() {
		return id;
	}

	public Card getCard() {
		return card;
	}

	public Long getDeckId() {
		return deckId;
	}

	public Long getMemberId() {
		return memberId;
	}

	public Instant getDueAt() {
		return dueAt;
	}

	public int getIntervalDays() {
		return intervalDays;
	}

	public int getRepetitions() {
		return repetitions;
	}

	public int getLapses() {
		return lapses;
	}

	public Long getVersion() {
		return version;
	}

	@Override
	public String toString() {
		return "ReviewState{id=" + id + ", dueAt=" + dueAt + ", version=" + version + "}";
	}

}
