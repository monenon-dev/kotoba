package com.kotoba.backend.security;

import java.io.IOException;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * 요청마다 한 번 실행되어 Authorization: Bearer 토큰을 검증한다.
 *
 * 흐름:
 * 1. 헤더가 없거나 Bearer 가 아니면 아무것도 하지 않고 다음 필터로 넘긴다 (익명 요청).
 * 2. 토큰이 유효하면 SecurityContext 에 인증 정보를 넣는다.
 * 3. 토큰이 유효하지 않으면 인증 정보를 넣지 않는다.
 * 실제 401 응답은 이 필터가 아니라, 인증이 필요한 경로에서 AuthenticationEntryPoint 가 만든다.
 *
 * 빈으로 등록하지 않고 SecurityConfig 에서 직접 생성한다. 빈이면 서블릿 필터로도 자동 등록되어 두 번 실행될 수 있다.
 */
public class JwtAuthenticationFilter extends OncePerRequestFilter {

	private static final Logger log = LoggerFactory.getLogger(JwtAuthenticationFilter.class);

	private static final String BEARER_PREFIX = "Bearer ";

	private final JwtTokenProvider tokenProvider;

	public JwtAuthenticationFilter(JwtTokenProvider tokenProvider) {
		this.tokenProvider = tokenProvider;
	}

	@Override
	protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
			throws ServletException, IOException {
		String header = request.getHeader(HttpHeaders.AUTHORIZATION);
		if (header != null && header.startsWith(BEARER_PREFIX)) {
			String token = header.substring(BEARER_PREFIX.length()).trim();
			try {
				AuthenticatedMember principal = tokenProvider.parse(token);
				var authorities = List.of(new SimpleGrantedAuthority("ROLE_" + principal.role()));
				var authentication = new UsernamePasswordAuthenticationToken(principal, null, authorities);
				SecurityContext context = SecurityContextHolder.createEmptyContext();
				context.setAuthentication(authentication);
				SecurityContextHolder.setContext(context);
			}
			catch (JwtException | IllegalArgumentException e) {
				// 토큰 원문은 로그에 남기지 않는다.
				log.debug("Rejected access token: {}", e.getClass().getSimpleName());
				SecurityContextHolder.clearContext();
			}
		}
		chain.doFilter(request, response);
	}

}
