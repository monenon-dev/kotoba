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

import com.kotoba.backend.controller.dto.DeckRequest;
import com.kotoba.backend.controller.dto.DeckResponse;
import com.kotoba.backend.controller.dto.PageResponse;
import com.kotoba.backend.security.AuthenticatedMember;
import com.kotoba.backend.service.DeckService;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/decks")
public class DeckController {

	private final DeckService deckService;

	public DeckController(DeckService deckService) {
		this.deckService = deckService;
	}

	@PostMapping
	@ResponseStatus(HttpStatus.CREATED)
	public DeckResponse create(@AuthenticationPrincipal AuthenticatedMember member,
			@Valid @RequestBody DeckRequest request) {
		return deckService.create(member.id(), request);
	}

	// page, size 만 사용한다. size 상한은 spring.data.web.pageable.max-page-size 로 제한된다.
	@GetMapping
	public PageResponse<DeckResponse> list(@AuthenticationPrincipal AuthenticatedMember member, Pageable pageable) {
		return deckService.list(member.id(), pageable);
	}

	@GetMapping("/{deckId}")
	public DeckResponse get(@AuthenticationPrincipal AuthenticatedMember member, @PathVariable Long deckId) {
		return deckService.get(member.id(), deckId);
	}

	@PutMapping("/{deckId}")
	public DeckResponse update(@AuthenticationPrincipal AuthenticatedMember member, @PathVariable Long deckId,
			@Valid @RequestBody DeckRequest request) {
		return deckService.update(member.id(), deckId, request);
	}

	@DeleteMapping("/{deckId}")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void delete(@AuthenticationPrincipal AuthenticatedMember member, @PathVariable Long deckId) {
		deckService.delete(member.id(), deckId);
	}

}
