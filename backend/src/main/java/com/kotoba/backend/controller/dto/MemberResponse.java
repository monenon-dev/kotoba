package com.kotoba.backend.controller.dto;

import java.time.Instant;

import com.kotoba.backend.domain.Member;

public record MemberResponse(Long id, String email, String role, Instant createdAt) {

	public static MemberResponse from(Member member) {
		return new MemberResponse(member.getId(), member.getEmail(), member.getRole().name(), member.getCreatedAt());
	}

}
