package com.kotoba.backend.config;

import java.time.ZoneId;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * app.study.* 설정. 잘못된 시간대 문자열은 바인딩 단계에서, 음수 한도는 생성자에서 기동을 실패시킨다.
 *
 * @param zone "오늘", 연속 학습일, 날짜별 통계, 다음 복습 시각의 날짜 경계로 쓰는 기준 시간대
 * @param dailyNewCardLimit 회원당(모든 덱 합산) 하루에 새로 시작하는 카드 수 상한
 */
@ConfigurationProperties("app.study")
public record StudyProperties(
		@DefaultValue("Asia/Seoul") ZoneId zone,
		@DefaultValue("20") int dailyNewCardLimit) {

	public StudyProperties {
		if (dailyNewCardLimit < 0) {
			throw new IllegalStateException("app.study.daily-new-card-limit 은 0 이상이어야 합니다.");
		}
	}

}
