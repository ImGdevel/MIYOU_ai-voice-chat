package com.study.webflux.rag.application.dialogue.controller;

import lombok.RequiredArgsConstructor;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.study.webflux.rag.domain.dialogue.model.ConversationSession;
import com.study.webflux.rag.domain.dialogue.model.ConversationSessionId;
import com.study.webflux.rag.domain.dialogue.model.PersonaId;
import com.study.webflux.rag.domain.dialogue.model.UserId;
import com.study.webflux.rag.domain.dialogue.port.ConversationSessionRepository;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@RestController
@RequiredArgsConstructor
@RequestMapping("/rag/session")
public class ConversationSessionController {

	private final ConversationSessionRepository sessionRepository;

	/** 새 세션을 생성합니다. */
	@PostMapping
	@ResponseStatus(HttpStatus.CREATED)
	public Mono<ConversationSession> createSession(
		@Valid @RequestBody CreateSessionRequest request) {
		ConversationSession session = ConversationSession.create(
			PersonaId.of(request.personaId()),
			UserId.of(request.userId()));
		return sessionRepository.save(session);
	}

	/** 세션을 soft delete 처리합니다. */
	@DeleteMapping("/{sessionId}")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public Mono<Void> deleteSession(@PathVariable String sessionId) {
		return sessionRepository.softDelete(ConversationSessionId.of(sessionId)).then();
	}

	/** 세션 목록을 조회합니다. */
	@GetMapping
	public Flux<ConversationSession> getSessions(
		@RequestParam(required = false) String userId,
		@RequestParam(required = false) String personaId) {
		if (personaId != null && userId != null) {
			return sessionRepository.findByPersonaIdAndUserId(
				PersonaId.of(personaId),
				UserId.of(userId));
		} else if (personaId != null) {
			return sessionRepository.findByPersonaId(PersonaId.of(personaId));
		} else if (userId != null) {
			return sessionRepository.findByUserId(UserId.of(userId));
		}
		return Flux.empty();
	}

	@Schema(description = "세션 생성 Request")
	public record CreateSessionRequest(
		@Schema(description = "페르소나 ID", example = "maid") @NotBlank String personaId,

		@Schema(description = "사용자 ID", example = "550e8400-e29b-41d4-a716-446655440000") @NotBlank String userId) {
	}
}
