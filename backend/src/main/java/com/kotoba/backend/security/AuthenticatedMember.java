package com.kotoba.backend.security;

/**
 * 인증된 요청의 principal. Access Token 에서 꺼낸 정보만 담는다.
 * 컨트롤러에서는 {@code @AuthenticationPrincipal AuthenticatedMember} 로 받는다.
 */
public record AuthenticatedMember(Long id, String role) {
}
