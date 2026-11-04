package com.kotoba.backend.exception;

import java.util.List;

/**
 * 모든 에러 응답의 공통 형식.
 * <pre>
 * { "status": 400, "code": "INVALID_INPUT", "message": "...", "errors": [ { "field": "email", "message": "..." } ] }
 * </pre>
 * 입력값(rejectedValue)은 비밀번호가 섞일 수 있으므로 응답에 넣지 않는다.
 */
public record ErrorResponse(int status, String code, String message, List<FieldError> errors) {

	public static ErrorResponse of(ErrorCode errorCode) {
		return of(errorCode, List.of());
	}

	public static ErrorResponse of(ErrorCode errorCode, List<FieldError> errors) {
		return new ErrorResponse(errorCode.getStatus().value(), errorCode.name(), errorCode.getMessage(), errors);
	}

	public record FieldError(String field, String message) {
	}

}
