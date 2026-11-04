package com.kotoba.backend.repository;

import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.kotoba.backend.domain.Deck;

public interface DeckRepository extends JpaRepository<Deck, Long> {

	// 소유자 조건을 쿼리에 포함한다. 없거나 남의 덱이면 둘 다 empty → 404.
	Optional<Deck> findByIdAndMemberId(Long id, Long memberId);

	Page<Deck> findByMemberId(Long memberId, Pageable pageable);

	/**
	 * 덱 한 건을 벌크 DELETE 로 지운다. CardRepository#deleteAllByDeckId 다음에 호출한다.
	 * flushAutomatically: 실행 전에 쌓여 있던 변경을 먼저 DB 에 반영한다 (clear 로 유실되지 않도록).
	 * clearAutomatically: 실행 후 영속성 컨텍스트를 비워, 지워진 엔티티가 1차 캐시에 남지 않게 한다.
	 */
	@Modifying(flushAutomatically = true, clearAutomatically = true)
	@Query("delete from Deck d where d.id = :deckId")
	int deleteInBulkById(@Param("deckId") Long deckId);

}
