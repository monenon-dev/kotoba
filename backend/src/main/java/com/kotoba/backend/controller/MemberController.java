package com.kotoba.backend.controller;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.kotoba.backend.controller.dto.MemberResponse;
import com.kotoba.backend.security.AuthenticatedMember;
import com.kotoba.backend.service.MemberService;

@RestController
@RequestMapping("/api/users")
public class MemberController {

	private final MemberService memberService;

	public MemberController(MemberService memberService) {
		this.memberService = memberService;
	}

	@GetMapping("/me")
	public MemberResponse me(@AuthenticationPrincipal AuthenticatedMember member) {
		return memberService.getMe(member.id());
	}

}
