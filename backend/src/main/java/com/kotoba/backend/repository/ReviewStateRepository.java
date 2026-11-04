package com.kotoba.backend.repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.kotoba.backend.domain.ReviewState;

public interface ReviewStateRepository extends JpaRepository<ReviewState, Long> {

	Optional<ReviewState> findByCardId(Long cardId);

	/**
	 * 덱에서 기한이 지난 카드. 카드 내용까지 JOIN FETCH 로 한 번에 가져온다 (카드마다 조회하지 않도록).
	 * (deck_id, due_at) 인덱스로 범위 스캔한다.
	 */
	@Query("""
			select s from ReviewState s join fetch s.card
			where s.deckId = :deckId and s.dueAt <= :now
			order by s.dueAt asc, s.id asc
			""")
	List<ReviewState> findDue(@Param("deckId") Long deckId, @Param("now") Instant now, Pageable pageable);

	// (member_id, due_at) 인덱스로 센다. 신규 카드(상태 행 없음)는 포함되지 않는다.
	long countByMemberIdAndDueAtLessThanEqual(Long memberId, Instant now);

	// 덱 삭제 시 카드보다 먼저 지운다. flush/clear 이유는 CardRepository#deleteAllByDeckId 참고.
	@Modifying(flushAutomatically = true, clearAutomatically = true)
	@Query("delete from ReviewState s where s.deckId = :deckId")
	int deleteAllByDeckId(@Param("deckId") Long deckId);

	@Modifying(flushAutomatically = true, clearAutomatically = true)
	@Query("delete from ReviewState s where s.card.id = :cardId")
	int deleteByCardId(@Param("cardId") Long cardId);

}
