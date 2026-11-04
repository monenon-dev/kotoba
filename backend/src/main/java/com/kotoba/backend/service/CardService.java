package com.kotoba.backend.service;

import java.time.Clock;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.kotoba.backend.controller.dto.CardRequest;
import com.kotoba.backend.controller.dto.CardResponse;
import com.kotoba.backend.controller.dto.PageResponse;
import com.kotoba.backend.domain.Card;
import com.kotoba.backend.domain.Deck;
import com.kotoba.backend.exception.BusinessException;
import com.kotoba.backend.exception.ErrorCode;
import com.kotoba.backend.repository.CardRepository;
import com.kotoba.backend.repository.ReviewStateRepository;

@Service
public class CardService {

	// 추가한 순서대로 보여 준다 (학습 순서).
	private static final Sort CARD_SORT = Sort.by(Sort.Order.asc("createdAt"), Sort.Order.asc("id"));

	private final CardRepository cardRepository;

	private final DeckService deckService;

	private final ReviewStateRepository reviewStateRepository;

	private final Clock clock;

	public CardService(CardRepository cardRepository, DeckService deckService,
			ReviewStateRepository reviewStateRepository, Clock clock) {
		this.cardRepository = cardRepository;
		this.deckService = deckService;
		this.reviewStateRepository = reviewStateRepository;
		this.clock = clock;
	}

	@Transactional
	public CardResponse create(Long memberId, Long deckId, CardRequest request) {
		Deck deck = deckService.findOwnedDeck(memberId, deckId);
		Card card = cardRepository.save(new Card(deck, request.text(), request.reading(), request.meaning(),
				request.example(), clock.instant()));
		return CardResponse.from(card);
	}

	@Transactional(readOnly = true)
	public PageResponse<CardResponse> list(Long memberId, Long deckId, Pageable pageable) {
		// 남의 덱이면 여기서 404. 카드 조회는 소유가 확인된 덱 id 로만 한다.
		deckService.findOwnedDeck(memberId, deckId);
		return PageResponse.of(cardRepository.findByDeckId(deckId,
				PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(), CARD_SORT)), CardResponse::from);
	}

	@Transactional(readOnly = true)
	public CardResponse get(Long memberId, Long deckId, Long cardId) {
		return CardResponse.from(findOwnedCard(memberId, deckId, cardId));
	}

	@Transactional
	public CardResponse update(Long memberId, Long deckId, Long cardId, CardRequest request) {
		Card card = findOwnedCard(memberId, deckId, cardId);
		card.update(request.text(), request.reading(), request.meaning(), request.example(), clock.instant());
		return CardResponse.from(card);
	}

	/**
	 * 복습 상태를 먼저 지우고 카드를 지운다 (외래키). 리뷰 로그는 남긴다.
	 * 상태 벌크 DELETE 가 영속성 컨텍스트를 비우므로 카드는 id 로 다시 찾아 지운다.
	 */
	@Transactional
	public void delete(Long memberId, Long deckId, Long cardId) {
		findOwnedCard(memberId, deckId, cardId);
		reviewStateRepository.deleteByCardId(cardId);
		cardRepository.deleteById(cardId);
	}

	private Card findOwnedCard(Long memberId, Long deckId, Long cardId) {
		return cardRepository.findOwned(cardId, deckId, memberId)
			.orElseThrow(() -> new BusinessException(ErrorCode.CARD_NOT_FOUND));
	}

}
