package com.kotoba.backend.config;

import java.time.Clock;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

import com.kotoba.backend.security.JsonErrorResponder;
import com.kotoba.backend.security.JwtAuthenticationFilter;
import com.kotoba.backend.security.JwtProperties;
import com.kotoba.backend.security.JwtTokenProvider;

@Configuration
@EnableConfigurationProperties(JwtProperties.class)
public class SecurityConfig {

	@Bean
	public SecurityFilterChain securityFilterChain(HttpSecurity http, JwtTokenProvider tokenProvider,
			JsonErrorResponder errorResponder,
			@Value("${spring.h2.console.enabled:false}") boolean h2ConsoleEnabled) throws Exception {
		http
			// 세션·쿠키를 쓰지 않고 매 요청 Authorization 헤더로 인증하므로 CSRF 보호가 필요 없다.
			.csrf(AbstractHttpConfigurer::disable)
			.formLogin(AbstractHttpConfigurer::disable)
			.httpBasic(AbstractHttpConfigurer::disable)
			.logout(AbstractHttpConfigurer::disable)
			.sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
			.exceptionHandling(ex -> ex.authenticationEntryPoint(errorResponder).accessDeniedHandler(errorResponder))
			.authorizeHttpRequests(auth -> {
				auth.requestMatchers(HttpMethod.GET, "/api/health", "/actuator/health").permitAll();
				auth.requestMatchers(HttpMethod.POST, "/api/auth/signup", "/api/auth/login", "/api/auth/refresh")
					.permitAll();
				// 컨트롤러 예외가 /error 로 포워드될 때 401 로 덮이지 않게 한다.
				auth.requestMatchers("/error").permitAll();
				if (h2ConsoleEnabled) {
					auth.requestMatchers("/h2-console/**").permitAll();
				}
				auth.anyRequest().authenticated();
			})
			.addFilterBefore(new JwtAuthenticationFilter(tokenProvider), UsernamePasswordAuthenticationFilter.class);

		if (h2ConsoleEnabled) {
			// H2 콘솔은 iframe 으로 동작한다 (dev 전용).
			http.headers(headers -> headers.frameOptions(frame -> frame.sameOrigin()));
		}
		return http.build();
	}

	@Bean
	public PasswordEncoder passwordEncoder() {
		return new BCryptPasswordEncoder();
	}

	// 현재 시각을 Clock 으로 주입받으면 테스트에서 만료 상황을 재현할 수 있다.
	@Bean
	public Clock clock() {
		return Clock.systemUTC();
	}

}
