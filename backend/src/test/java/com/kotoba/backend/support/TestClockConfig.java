package com.kotoba.backend.support;

import java.time.Instant;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

@TestConfiguration
public class TestClockConfig {

	@Bean
	@Primary
	public MutableClock testClock() {
		return new MutableClock(Instant.now());
	}

}
