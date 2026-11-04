package com.kotoba.backend.controller.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * /api/auth/refresh 와 /api/auth/logout 에서 공통으로 쓰는 요청.
 */
public record RefreshTokenRequest(
		@NotBlank(message = "refreshToken 을 입력해 주세요.")
		String refreshToken) {

	@Override
	public String toString() {
		return "RefreshTokenRequest[refreshToken=****]";
	}

}
