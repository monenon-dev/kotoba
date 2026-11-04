package com.kotoba.backend.repository;

/**
 * 덱별 카드 수 집계 결과 (인터페이스 기반 projection).
 */
public interface DeckCardCount {

	Long getDeckId();

	long getCardCount();

}
