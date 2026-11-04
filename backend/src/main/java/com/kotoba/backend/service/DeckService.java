package com.kotoba.backend.service;

import java.time.Clock;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.kotoba.backend.controller.dto.DeckRequest;
import com.kotoba.backend.controller.dto.DeckResponse;
import com.kotoba.backend.controller.dto.PageResponse;
import com.kotoba.backend.domain.Deck;
import com.kotoba.backend.domain.Member;
import com.kotoba.backend.exception.BusinessException;
import com.kotoba.backend.exception.ErrorCode;
import com.kotoba.backend.repository.CardRepository;
import com.kotoba.backend.repository.DeckCardCount;
import com.kotoba.backend.repository.DeckRepository;
import com.kotoba.backend.repository.MemberRepository;

@Service
public class DeckService {

	// 클라이언트의 sort 파라미터는 무시하고 서버가 정렬을 고정한다. 최근 만든 덱이 먼저.
	private static final Sort DECK_SORT = Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id"));

	private final DeckRepository deckRepository;

	private final CardRepository cardRepository;

	private final MemberRepository memberRepository;

	private final Clock clock;

	public DeckService(DeckRepository deckRepository, CardRepository cardRepository,
			MemberRepository memberRepository, Clock clock) {
		this.deckRepository = deckRepository;
		this.cardRepository = cardRepository;
		this.memberRepository = memberRepository;
		this.clock = clock;
	}

	@Transactional
	public DeckResponse create(Long memberId, DeckRequest request) {
		// 회원 행을 SELECT 하지 않고 FK 만 채우는 프록시 참조를 쓴다.
		Member owner = memberRepository.getReferenceById(memberId);
		Deck deck = deckRepository.save(new Deck(owner, request.name(), request.description(), clock.instant()));
		return DeckResponse.of(deck, 0);
	}

	/**
	 * 덱 목록. 덱 개수와 무관하게 쿼리 수가 고정된다.
	 * 1) 덱 페이지 2) 전체 개수(필요할 때만) 3) 페이지에 포함된 덱들의 카드 수를 IN + GROUP BY 로 한 번에 집계
	 */
	@Transactional(readOnly = true)
	public PageResponse<DeckResponse> list(Long memberId, Pageable pageable) {
		Page<Deck> decks = deckRepository.findByMemberId(memberId,
				PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(), DECK_SORT));

		List<Long> deckIds = decks.getContent().stream().map(Deck::getId).toList();
		Map<Long, Long> cardCounts = deckIds.isEmpty() ? Map.of()
				: cardRepository.countByDeckIds(deckIds)
					.stream()
					.collect(Collectors.toMap(DeckCardCount::getDeckId, DeckCardCount::getCardCount));

		// 카드가 0장인 덱은 GROUP BY 결과에 없으므로 0 으로 채운다.
		return PageResponse.of(decks, deck -> DeckResponse.of(deck, cardCounts.getOrDefault(deck.getId(), 0L)));
	}

	@Transactional(readOnly = true)
	public DeckResponse get(Long memberId, Long deckId) {
		Deck deck = findOwnedDeck(memberId, deckId);
		return DeckResponse.of(deck, cardRepository.countByDeckId(deckId));
	}

	@Transactional
	public DeckResponse update(Long memberId, Long deckId, DeckRequest request) {
		Deck deck = findOwnedDeck(memberId, deckId);
		deck.update(request.name(), request.description(), clock.instant());
		return DeckResponse.of(deck, cardRepository.countByDeckId(deckId));
	}

	/**
	 * 덱과 속한 카드를 같은 트랜잭션에서 지운다. 카드 벌크 DELETE → 덱 벌크 DELETE 순서.
	 * 카드를 먼저 지워야 덱의 외래키 제약에 걸리지 않는다.
	 * 두 벌크 쿼리 모두 실행 전 flush, 실행 후 clear 하므로 영속성 컨텍스트가 DB 와 어긋나지 않는다.
	 */
	@Transactional
	public void delete(Long memberId, Long deckId) {
		findOwnedDeck(memberId, deckId);
		cardRepository.deleteAllByDeckId(deckId);
		deckRepository.deleteInBulkById(deckId);
	}

	Deck findOwnedDeck(Long memberId, Long deckId) {
		return deckRepository.findByIdAndMemberId(deckId, memberId)
			.orElseThrow(() -> new BusinessException(ErrorCode.DECK_NOT_FOUND));
	}

}
