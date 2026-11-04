package com.kotoba.backend.srs;

/**
 * 복습 후 사용자가 매기는 평가. AGAIN 은 "기억하지 못함"(오답), 나머지는 정답으로 본다.
 */
public enum Rating {

	AGAIN, HARD, GOOD, EASY;

	public boolean isCorrect() {
		return this != AGAIN;
	}

}
