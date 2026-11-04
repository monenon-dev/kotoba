package com.kotoba.backend.controller;

import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.kotoba.backend.controller.dto.CardRequest;
import com.kotoba.backend.controller.dto.CardResponse;
import com.kotoba.backend.controller.dto.PageResponse;
import com.kotoba.backend.security.AuthenticatedMember;
import com.kotoba.backend.service.CardService;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/decks/{deckId}/cards")
public class CardController {

	private final CardService cardService;

	public CardController(CardService cardService) {
		this.cardService = cardService;
	}

	@PostMapping
	@ResponseStatus(HttpStatus.CREATED)
	public CardResponse create(@AuthenticationPrincipal AuthenticatedMember member, @PathVariable Long deckId,
			@Valid @RequestBody CardRequest request) {
		return cardService.create(member.id(), deckId, request);
	}

	@GetMapping
	public PageResponse<CardResponse> list(@AuthenticationPrincipal AuthenticatedMember member,
			@PathVariable Long deckId, Pageable pageable) {
		return cardService.list(member.id(), deckId, pageable);
	}

	@GetMapping("/{cardId}")
	public CardResponse get(@AuthenticationPrincipal AuthenticatedMember member, @PathVariable Long deckId,
			@PathVariable Long cardId) {
		return cardService.get(member.id(), deckId, cardId);
	}

	@PutMapping("/{cardId}")
	public CardResponse update(@AuthenticationPrincipal AuthenticatedMember member, @PathVariable Long deckId,
			@PathVariable Long cardId, @Valid @RequestBody CardRequest request) {
		return cardService.update(member.id(), deckId, cardId, request);
	}

	@DeleteMapping("/{cardId}")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void delete(@AuthenticationPrincipal AuthenticatedMember member, @PathVariable Long deckId,
			@PathVariable Long cardId) {
		cardService.delete(member.id(), deckId, cardId);
	}

}
