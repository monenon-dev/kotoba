package com.kotoba.backend.exception;

import java.util.List;

/**
 * 서비스 계층에서 의도적으로 던지는 예외. 전역 예외 처리기가 ErrorCode 에 맞는 응답으로 바꾼다.
 */
public class BusinessException extends RuntimeException {

	private final ErrorCode errorCode;

	private final List<ErrorResponse.FieldError> errors;

	public BusinessException(ErrorCode errorCode) {
		this(errorCode, List.of());
	}

	public BusinessException(ErrorCode errorCode, List<ErrorResponse.FieldError> errors) {
		super(errorCode.name());
		this.errorCode = errorCode;
		this.errors = errors;
	}

	/** 요청 파라미터 하나가 규칙에 맞지 않을 때 (400 INVALID_INPUT + 필드 에러). */
	public static BusinessException invalidInput(String field, String message) {
		return new BusinessException(ErrorCode.INVALID_INPUT, List.of(new ErrorResponse.FieldError(field, message)));
	}

	public ErrorCode getErrorCode() {
		return errorCode;
	}

	public List<ErrorResponse.FieldError> getErrors() {
		return errors;
	}

}
