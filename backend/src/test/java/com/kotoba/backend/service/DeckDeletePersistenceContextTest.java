package com.kotoba.backend.service;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.support.TransactionTemplate;

import com.kotoba.backend.domain.Card;
import com.kotoba.backend.domain.Deck;
import com.kotoba.backend.domain.Member;
import com.kotoba.backend.support.IntegrationTestSupport;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;

/**
 * 벌크 DELETE 는 영속성 컨텍스트를 거치지 않는다. 같은 트랜잭션 안에서
 * (1) 이미 로딩된 카드가 1차 캐시에 남아 "살아 있는" 것처럼 보이지 않는지 (clearAutomatically)
 * (2) 삭제 전에 수정해 둔 다른 엔티티의 변경이 clear 로 유실되지 않는지
 * 를 확인한다.
 *
 * (2) 는 현재 Hibernate 기본 flush 모드(AUTO)가 벌크 쿼리 전에 스스로 flush 해서도 통과한다.
 * flushAutomatically 는 flush 모드가 바뀌어도(COMMIT 등) 이 동작을 보장하기 위한 명시적 장치이고,
 * 이 테스트는 "변경이 유실되지 않는다"는 결과를 지킨다.
 */
class DeckDeletePersistenceContextTest extends IntegrationTestSupport {

	@Autowired
	private DeckService deckService;

	@Autowired
	private TransactionTemplate transactionTemplate;

	@PersistenceContext
	private EntityManager entityManager;

	@Test
	void bulkDeleteKeepsPersistenceContextInSyncWithDatabase() {
		Member owner = memberRepository.save(new Member("owner@example.com", "hash", clock.instant()));
		Deck target = deckRepository.save(new Deck(owner, "target", null, clock.instant()));
		Deck other = deckRepository.save(new Deck(owner, "other", null, clock.instant()));
		Card card = cardRepository.save(new Card(target, "勉強", null, "공부", null, clock.instant()));

		transactionTemplate.executeWithoutResult(status -> {
			// 같은 트랜잭션에서 카드를 미리 로딩하고, 다른 덱은 수정만 해 둔다 (아직 flush 전).
			Card loadedCard = cardRepository.findById(card.getId()).orElseThrow();
			Deck loadedOther = deckRepository.findById(other.getId()).orElseThrow();
			loadedOther.update("renamed", null, clock.instant());

			deckService.delete(owner.getId(), target.getId());

			// (1) 벌크 DELETE 후 컨텍스트가 비워져서, 지워진 카드를 캐시에서 돌려주지 않는다.
			assertThat(entityManager.contains(loadedCard)).isFalse();
			assertThat(cardRepository.findById(card.getId())).isEmpty();
			assertThat(deckRepository.findById(target.getId())).isEmpty();
		});

		// (2) clear 전에 flush 됐으므로 다른 덱의 수정 내용이 커밋되었다.
		assertThat(deckRepository.findById(other.getId()).orElseThrow().getName()).isEqualTo("renamed");
	}

}
