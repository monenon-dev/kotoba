package com.kotoba.backend.exception;

import org.springframework.http.HttpStatus;

public enum ErrorCode {

	INVALID_INPUT(HttpStatus.BAD_REQUEST, "요청 값이 올바르지 않습니다."),
	MALFORMED_REQUEST(HttpStatus.BAD_REQUEST, "요청 본문을 읽을 수 없습니다."),
	UNAUTHORIZED(HttpStatus.UNAUTHORIZED, "인증이 필요합니다."),
	INVALID_CREDENTIALS(HttpStatus.UNAUTHORIZED, "이메일 또는 비밀번호가 올바르지 않습니다."),
	// 만료·위조·재사용을 구분하지 않고 같은 응답을 준다. 공격자에게 힌트를 주지 않기 위함.
	INVALID_REFRESH_TOKEN(HttpStatus.UNAUTHORIZED, "유효하지 않은 refresh token 입니다. 다시 로그인해 주세요."),
	FORBIDDEN(HttpStatus.FORBIDDEN, "접근 권한이 없습니다."),
	NOT_FOUND(HttpStatus.NOT_FOUND, "요청한 리소스를 찾을 수 없습니다."),
	// 없는 경우와 남의 것인 경우를 구분하지 않는다 (존재 여부를 드러내지 않기 위해 403 대신 404).
	DECK_NOT_FOUND(HttpStatus.NOT_FOUND, "덱을 찾을 수 없습니다."),
	CARD_NOT_FOUND(HttpStatus.NOT_FOUND, "카드를 찾을 수 없습니다."),
	METHOD_NOT_ALLOWED(HttpStatus.METHOD_NOT_ALLOWED, "지원하지 않는 HTTP 메서드입니다."),
	EMAIL_ALREADY_EXISTS(HttpStatus.CONFLICT, "이미 가입된 이메일입니다."),
	// 같은 카드의 복습이 동시에 처리되어 낙관적 락(@Version) 또는 UNIQUE(card_id) 충돌이 난 경우.
	REVIEW_CONFLICT(HttpStatus.CONFLICT, "같은 카드의 복습이 동시에 처리되었습니다. 목록을 새로고침해 주세요."),
	INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "서버 오류가 발생했습니다.");

	private final HttpStatus status;

	private final String message;

	ErrorCode(HttpStatus status, String message) {
		this.status = status;
		this.message = message;
	}

	public HttpStatus getStatus() {
		return status;
	}

	public String getMessage() {
		return message;
	}

}
