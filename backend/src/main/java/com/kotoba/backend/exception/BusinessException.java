package com.kotoba.backend.exception;

/**
 * 서비스 계층에서 의도적으로 던지는 예외. 전역 예외 처리기가 ErrorCode 에 맞는 응답으로 바꾼다.
 */
public class BusinessException extends RuntimeException {

	private final ErrorCode errorCode;

	public BusinessException(ErrorCode errorCode) {
		super(errorCode.name());
		this.errorCode = errorCode;
	}

	public ErrorCode getErrorCode() {
		return errorCode;
	}

}
