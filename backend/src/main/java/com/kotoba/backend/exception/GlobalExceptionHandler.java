package com.kotoba.backend.exception;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

@RestControllerAdvice
public class GlobalExceptionHandler {

	private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

	@ExceptionHandler(BusinessException.class)
	public ResponseEntity<ErrorResponse> handleBusiness(BusinessException e) {
		return toResponse(ErrorResponse.of(e.getErrorCode(), e.getErrors()));
	}

	// 필수 쿼리 파라미터 누락 (예: /api/stats/daily 의 from, to)
	@ExceptionHandler(MissingServletRequestParameterException.class)
	public ResponseEntity<ErrorResponse> handleMissingParameter(MissingServletRequestParameterException e) {
		var error = new ErrorResponse.FieldError(e.getParameterName(), "필수 값입니다.");
		return toResponse(ErrorResponse.of(ErrorCode.INVALID_INPUT, List.of(error)));
	}

	@ExceptionHandler(MethodArgumentNotValidException.class)
	public ResponseEntity<ErrorResponse> handleValidation(MethodArgumentNotValidException e) {
		List<ErrorResponse.FieldError> errors = e.getBindingResult()
			.getFieldErrors()
			.stream()
			.map(fe -> new ErrorResponse.FieldError(fe.getField(), fe.getDefaultMessage()))
			.toList();
		return toResponse(ErrorResponse.of(ErrorCode.INVALID_INPUT, errors));
	}

	// /api/decks/abc 처럼 경로·쿼리 파라미터의 타입이 맞지 않는 경우. 입력값은 응답에 넣지 않는다.
	@ExceptionHandler(MethodArgumentTypeMismatchException.class)
	public ResponseEntity<ErrorResponse> handleTypeMismatch(MethodArgumentTypeMismatchException e) {
		var error = new ErrorResponse.FieldError(e.getName(), "형식이 올바르지 않습니다.");
		return toResponse(ErrorResponse.of(ErrorCode.INVALID_INPUT, List.of(error)));
	}

	// 잘못된 JSON 등. 예외 메시지에 본문 일부가 들어 있을 수 있어 그대로 내보내지 않는다.
	@ExceptionHandler(HttpMessageNotReadableException.class)
	public ResponseEntity<ErrorResponse> handleNotReadable(HttpMessageNotReadableException e) {
		return toResponse(ErrorResponse.of(ErrorCode.MALFORMED_REQUEST));
	}

	@ExceptionHandler(HttpRequestMethodNotSupportedException.class)
	public ResponseEntity<ErrorResponse> handleMethodNotSupported(HttpRequestMethodNotSupportedException e) {
		return toResponse(ErrorResponse.of(ErrorCode.METHOD_NOT_ALLOWED));
	}

	@ExceptionHandler(NoResourceFoundException.class)
	public ResponseEntity<ErrorResponse> handleNoResource(NoResourceFoundException e) {
		return toResponse(ErrorResponse.of(ErrorCode.NOT_FOUND));
	}

	@ExceptionHandler(Exception.class)
	public ResponseEntity<ErrorResponse> handleUnexpected(Exception e) {
		log.error("Unhandled exception", e);
		return toResponse(ErrorResponse.of(ErrorCode.INTERNAL_ERROR));
	}

	private ResponseEntity<ErrorResponse> toResponse(ErrorResponse body) {
		return ResponseEntity.status(body.status()).body(body);
	}

}
