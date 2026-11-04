package com.kotoba.backend.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.kotoba.backend.controller.dto.MemberResponse;
import com.kotoba.backend.exception.BusinessException;
import com.kotoba.backend.exception.ErrorCode;
import com.kotoba.backend.repository.MemberRepository;

@Service
public class MemberService {

	private final MemberRepository memberRepository;

	public MemberService(MemberRepository memberRepository) {
		this.memberRepository = memberRepository;
	}

	@Transactional(readOnly = true)
	public MemberResponse getMe(Long memberId) {
		// 토큰은 유효하지만 회원이 삭제된 경우도 인증 실패로 본다.
		return memberRepository.findById(memberId)
			.map(MemberResponse::from)
			.orElseThrow(() -> new BusinessException(ErrorCode.UNAUTHORIZED));
	}

}
