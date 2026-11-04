package com.kotoba.backend.service;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.kotoba.backend.config.StudyProperties;
import com.kotoba.backend.controller.dto.DueCardsResponse;
import com.kotoba.backend.controller.dto.DueCardsResponse.DueCard;
import com.kotoba.backend.controller.dto.ReviewResponse;
import com.kotoba.backend.domain.Card;
import com.kotoba.backend.domain.ReviewLog;
import com.kotoba.backend.domain.ReviewState;
import com.kotoba.backend.exception.BusinessException;
import com.kotoba.backend.exception.ErrorCode;
import com.kotoba.backend.repository.CardRepository;
import com.kotoba.backend.repository.MemberRepository;
import com.kotoba.backend.repository.ReviewLogRepository;
import com.kotoba.backend.repository.ReviewStateRepository;
import com.kotoba.backend.srs.Rating;
import com.kotoba.backend.srs.ReviewSchedule;
import com.kotoba.backend.srs.ScheduleResult;
import com.kotoba.backend.srs.SrsScheduler;

@Service
public class StudyService {

	public static final int DEFAULT_DUE_LIMIT = 20;

	public static final int MAX_DUE_LIMIT = 100;

	private final DeckService deckService;

	private final CardRepository cardRepository;

	private final ReviewStateRepository reviewStateRepository;

	private final ReviewLogRepository reviewLogRepository;

	private final MemberRepository memberRepository;

	private final SrsScheduler scheduler;

	private final StudyProperties properties;

	private final Clock clock;

	public StudyService(DeckService deckService, CardRepository cardRepository,
			ReviewStateRepository reviewStateRepository, ReviewLogRepository reviewLogRepository,
			MemberRepository memberRepository, SrsScheduler scheduler, StudyProperties properties, Clock clock) {
		this.deckService = deckService;
		this.cardRepository = cardRepository;
		this.reviewStateRepository = reviewStateRepository;
		this.reviewLogRepository = reviewLogRepository;
		this.memberRepository = memberRepository;
		this.scheduler = scheduler;
		this.properties = properties;
		this.clock = clock;
	}

	/**
	 * 지금 복습할 카드: 기한이 지난 카드(due_at 오름차순) → 남은 자리를 신규 카드로 채움 (오늘 남은 신규 한도 이내).
	 * 쿼리는 카드 수와 무관하게 4개: 덱 소유 확인, 기한 카드(+카드 JOIN FETCH), 오늘 시작한 신규 수, 신규 카드.
	 */
	@Transactional(readOnly = true)
	public DueCardsResponse due(Long memberId, Long deckId, int requestedLimit) {
		if (requestedLimit < 1) {
			throw BusinessException.invalidInput("limit", "1 이상이어야 합니다.");
		}
		int limit = Math.min(requestedLimit, MAX_DUE_LIMIT);
		deckService.findOwnedDeck(memberId, deckId);

		Instant now = clock.instant();
		List<ReviewState> dueStates = reviewStateRepository.findDue(deckId, now, PageRequest.of(0, limit));

		int remainingNewToday = remainingNewCards(memberId, today(now));
		int newSlots = Math.min(limit - dueStates.size(), remainingNewToday);
		List<Card> newCards = newSlots > 0 ? cardRepository.findNewCards(deckId, PageRequest.of(0, newSlots))
				: List.of();

		List<DueCard> cards = new ArrayList<>(dueStates.size() + newCards.size());
		dueStates.forEach(state -> cards.add(DueCard.due(state)));
		newCards.forEach(card -> cards.add(DueCard.fresh(card)));
		return new DueCardsResponse(deckId, dueStates.size(), newCards.size(), remainingNewToday, cards);
	}

	/**
	 * 평가를 받아 복습 상태를 갱신하고 리뷰 로그를 남긴다. 둘은 같은 트랜잭션이라 충돌 시 로그도 롤백된다.
	 *
	 * 동시성: 상태 갱신은 saveAndFlush 로 메서드 안에서 바로 DB 에 반영해 충돌을 여기서 감지한다.
	 * - 기존 상태: @Version 불일치 → OptimisticLockingFailureException
	 * - 신규 카드 첫 복습이 동시에 들어옴: UNIQUE(card_id) 위반 → DataIntegrityViolationException
	 * 둘 다 409 REVIEW_CONFLICT 로 바꾼다.
	 */
	@Transactional
	public ReviewResponse review(Long memberId, Long deckId, Long cardId, Rating rating) {
		Card card = cardRepository.findOwned(cardId, deckId, memberId)
			.orElseThrow(() -> new BusinessException(ErrorCode.CARD_NOT_FOUND));

		ReviewState state = reviewStateRepository.findByCardId(cardId).orElse(null);
		boolean firstReview = state == null;
		ReviewSchedule current = firstReview ? ReviewSchedule.NEW : state.schedule();
		ScheduleResult result = scheduler.schedule(current, rating);

		if (firstReview) {
			state = new ReviewState(card, deckId, memberId, result);
		}
		else {
			state.apply(result);
		}
		try {
			state = reviewStateRepository.saveAndFlush(state);
		}
		catch (OptimisticLockingFailureException | DataIntegrityViolationException e) {
			throw new BusinessException(ErrorCode.REVIEW_CONFLICT);
		}

		Integer previousInterval = firstReview ? null : current.intervalDays();
		reviewLogRepository.save(new ReviewLog(memberRepository.getReferenceById(memberId), deckId, cardId, rating,
				firstReview, previousInterval, result.schedule().intervalDays(), result.reviewedAt(),
				today(result.reviewedAt())));

		return ReviewResponse.of(cardId, rating, result.reviewedAt(), previousInterval, state);
	}

	private int remainingNewCards(Long memberId, LocalDate today) {
		long startedToday = reviewLogRepository.countByMemberIdAndReviewDateAndFirstReviewTrue(memberId, today);
		return (int) Math.max(0, properties.dailyNewCardLimit() - startedToday);
	}

	// 기준 시간대의 날짜. DB 함수가 아니라 Java 에서 계산한다 (DB 이식성).
	private LocalDate today(Instant instant) {
		return instant.atZone(properties.zone()).toLocalDate();
	}

}
