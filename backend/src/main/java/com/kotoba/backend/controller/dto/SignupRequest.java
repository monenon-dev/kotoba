package com.kotoba.backend.controller.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record SignupRequest(
		@NotBlank(message = "이메일을 입력해 주세요.")
		@Email(message = "이메일 형식이 올바르지 않습니다.")
		@Size(max = 255, message = "이메일은 255자 이하여야 합니다.")
		String email,

		// BCrypt 는 72바이트까지만 사용하므로 상한을 둔다.
		@NotBlank(message = "비밀번호를 입력해 주세요.")
		@Size(min = 8, max = 72, message = "비밀번호는 8자 이상 72자 이하여야 합니다.")
		String password) {

	// record 의 기본 toString 은 모든 필드를 출력하므로 비밀번호를 가린다.
	@Override
	public String toString() {
		return "SignupRequest[email=" + email + ", password=****]";
	}

}
