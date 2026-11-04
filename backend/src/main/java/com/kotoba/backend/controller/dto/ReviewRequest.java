package com.kotoba.backend.controller.dto;

import com.kotoba.backend.srs.Rating;

import jakarta.validation.constraints.NotNull;

/**
 * 복습 평가. 정의되지 않은 값(예: "WRONG")은 JSON 변환 단계에서 400 MALFORMED_REQUEST 가 된다.
 */
public record ReviewRequest(@NotNull(message = "평가(rating)를 입력해 주세요.") Rating rating) {
}
