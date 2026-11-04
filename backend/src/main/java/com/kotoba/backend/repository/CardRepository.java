package com.kotoba.backend.repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.kotoba.backend.domain.Card;

public interface CardRepository extends JpaRepository<Card, Long> {

	// 카드 id, URL 의 덱 id, 소유자가 모두 맞아야 조회된다.
	@Query("""
			select c from Card c
			where c.id = :cardId and c.deck.id = :deckId and c.deck.member.id = :memberId
			""")
	Optional<Card> findOwned(@Param("cardId") Long cardId, @Param("deckId") Long deckId,
			@Param("memberId") Long memberId);

	Page<Card> findByDeckId(Long deckId, Pageable pageable);

	// 신규 카드 = 복습 상태 행이 없는 카드. 추가한 순서대로. review_states 의 UNIQUE(card_id) 인덱스로 판별한다.
	@Query("""
			select c from Card c
			where c.deck.id = :deckId
			  and not exists (select 1 from ReviewState s where s.card = c)
			order by c.createdAt asc, c.id asc
			""")
	List<Card> findNewCards(@Param("deckId") Long deckId, Pageable pageable);

	long countByDeckId(Long deckId);

	/**
	 * 여러 덱의 카드 수를 한 번에 집계한다 (덱 목록의 N+1 방지).
	 * 카드가 0장인 덱은 결과에 없으므로 호출하는 쪽에서 0 으로 채운다.
	 */
	@Query("""
			select c.deck.id as deckId, count(c) as cardCount
			from Card c
			where c.deck.id in :deckIds
			group by c.deck.id
			""")
	List<DeckCardCount> countByDeckIds(@Param("deckIds") Collection<Long> deckIds);

	/**
	 * 덱에 속한 카드를 한 번의 DELETE 문으로 지운다.
	 * flushAutomatically: 실행 전에 쌓여 있던 변경을 먼저 DB 에 반영한다.
	 * clearAutomatically: 벌크 DELETE 는 영속성 컨텍스트를 거치지 않으므로, 이미 로딩된 Card 가
	 * 1차 캐시에 "살아 있는" 것처럼 남지 않도록 실행 후 컨텍스트를 비운다.
	 */
	@Modifying(flushAutomatically = true, clearAutomatically = true)
	@Query("delete from Card c where c.deck.id = :deckId")
	int deleteAllByDeckId(@Param("deckId") Long deckId);

}
