package com.kotoba.backend.controller;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.kotoba.backend.controller.dto.LoginRequest;
import com.kotoba.backend.controller.dto.MemberResponse;
import com.kotoba.backend.controller.dto.RefreshTokenRequest;
import com.kotoba.backend.controller.dto.SignupRequest;
import com.kotoba.backend.controller.dto.TokenResponse;
import com.kotoba.backend.security.AuthenticatedMember;
import com.kotoba.backend.service.AuthService;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

	private final AuthService authService;

	public AuthController(AuthService authService) {
		this.authService = authService;
	}

	@PostMapping("/signup")
	@ResponseStatus(HttpStatus.CREATED)
	public MemberResponse signup(@Valid @RequestBody SignupRequest request) {
		return authService.signup(request);
	}

	@PostMapping("/login")
	public TokenResponse login(@Valid @RequestBody LoginRequest request) {
		return authService.login(request);
	}

	@PostMapping("/refresh")
	public TokenResponse refresh(@Valid @RequestBody RefreshTokenRequest request) {
		return authService.refresh(request.refreshToken());
	}

	@PostMapping("/logout")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void logout(@AuthenticationPrincipal AuthenticatedMember member,
			@Valid @RequestBody RefreshTokenRequest request) {
		authService.logout(member.id(), request.refreshToken());
	}

}
