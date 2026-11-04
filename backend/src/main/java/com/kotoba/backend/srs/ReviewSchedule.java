package com.kotoba.backend.srs;

/**
 * 카드 한 장의 복습 상태 값.
 *
 * @param repetitions 연속 성공 횟수 (AGAIN 이면 0 으로 돌아감)
 * @param intervalDays 현재 간격(일). AGAIN 직후는 0
 * @param easePermille 난이도 계수 x1000 (2500 = 2.5). 정수로 다뤄 부동소수 오차가 쌓이지 않게 한다
 * @param lapses 실패(AGAIN) 누적 횟수
 */
public record ReviewSchedule(int repetitions, int intervalDays, int easePermille, int lapses) {

	/** 아직 한 번도 복습하지 않은 신규 카드의 상태. */
	public static final ReviewSchedule NEW = new ReviewSchedule(0, 0, SrsScheduler.EASE_INITIAL, 0);

	public double easeFactor() {
		return easePermille / 1000.0;
	}

}
