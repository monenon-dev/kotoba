package com.kotoba.backend.security;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;

/**
 * prod 처럼 JWT_SECRET 이 없거나 너무 짧으면 컨텍스트(=앱)가 기동되지 않는지 확인한다.
 */
class JwtPropertiesTest {

	private final ApplicationContextRunner runner = new ApplicationContextRunner()
		.withUserConfiguration(PropertiesConfig.class);

	@Test
	void failsToStartWithoutSecret() {
		runner.withPropertyValues("jwt.secret=").run(context -> {
			assertThat(context).hasFailed();
			assertThat(context.getStartupFailure()).rootCause().hasMessageContaining("JWT_SECRET");
		});
	}

	@Test
	void failsToStartWithShortSecret() {
		runner.withPropertyValues("jwt.secret=too-short").run(context -> {
			assertThat(context).hasFailed();
			assertThat(context.getStartupFailure()).rootCause().hasMessageContaining("32");
		});
	}

	@Test
	void startsWithValidSecretAndDefaults() {
		runner.withPropertyValues("jwt.secret=0123456789abcdef0123456789abcdef").run(context -> {
			assertThat(context).hasNotFailed();
			JwtProperties properties = context.getBean(JwtProperties.class);
			assertThat(properties.accessTokenTtl().toMinutes()).isEqualTo(30);
			assertThat(properties.toString()).doesNotContain("0123456789abcdef");
		});
	}

	@Configuration(proxyBeanMethods = false)
	@EnableConfigurationProperties(JwtProperties.class)
	static class PropertiesConfig {

	}

}
