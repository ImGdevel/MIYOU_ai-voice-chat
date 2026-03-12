package com.study.webflux.rag.infrastructure.inbound.web.dialogue.dto;

public record CreateSessionResponse(
	String sessionId,
	String userId,
	String personaId
) {
}
