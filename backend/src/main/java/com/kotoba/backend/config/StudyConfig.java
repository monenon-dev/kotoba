package com.kotoba.backend.config;

import java.time.Clock;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.kotoba.backend.srs.SrsScheduler;

@Configuration
@EnableConfigurationProperties(StudyProperties.class)
public class StudyConfig {

	// 스케줄러 자체는 순수 클래스이고, 여기서 Clock 과 기준 시간대를 주입해 빈으로 등록한다.
	@Bean
	public SrsScheduler srsScheduler(Clock clock, StudyProperties properties) {
		return new SrsScheduler(clock, properties.zone());
	}

}
