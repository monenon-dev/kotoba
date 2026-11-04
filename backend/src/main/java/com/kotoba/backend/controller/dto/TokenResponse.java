package com.kotoba.backend.controller.dto;

/**
 * 로그인/재발급 응답. expiresIn 값들은 초 단위.
 */
public record TokenResponse(
		String tokenType,
		String accessToken,
		long accessTokenExpiresIn,
		String refreshToken,
		long refreshTokenExpiresIn) {

	public static TokenResponse bearer(String accessToken, long accessTokenExpiresIn, String refreshToken,
			long refreshTokenExpiresIn) {
		return new TokenResponse("Bearer", accessToken, accessTokenExpiresIn, refreshToken, refreshTokenExpiresIn);
	}

	@Override
	public String toString() {
		return "TokenResponse[tokenType=" + tokenType + ", accessToken=****, refreshToken=****]";
	}

}
