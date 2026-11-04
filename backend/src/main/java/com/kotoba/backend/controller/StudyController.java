package com.kotoba.backend.controller;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.kotoba.backend.controller.dto.DueCardsResponse;
import com.kotoba.backend.controller.dto.ReviewRequest;
import com.kotoba.backend.controller.dto.ReviewResponse;
import com.kotoba.backend.security.AuthenticatedMember;
import com.kotoba.backend.service.StudyService;

import jakarta.validation.Valid;

@RestController
public class StudyController {

	private final StudyService studyService;

	public StudyController(StudyService studyService) {
		this.studyService = studyService;
	}

	// limit 기본 20, 100 초과는 100 으로 잘림, 1 미만은 400
	@GetMapping("/api/decks/{deckId}/study/due")
	public DueCardsResponse due(@AuthenticationPrincipal AuthenticatedMember member, @PathVariable Long deckId,
			@RequestParam(defaultValue = "" + StudyService.DEFAULT_DUE_LIMIT) int limit) {
		return studyService.due(member.id(), deckId, limit);
	}

	@PostMapping("/api/decks/{deckId}/cards/{cardId}/reviews")
	public ReviewResponse review(@AuthenticationPrincipal AuthenticatedMember member, @PathVariable Long deckId,
			@PathVariable Long cardId, @Valid @RequestBody ReviewRequest request) {
		return studyService.review(member.id(), deckId, cardId, request.rating());
	}

}
