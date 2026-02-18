package com.study.webflux.rag.application.dialogue.dto;

public record CreateSessionResponse(
	String sessionId,
	String userId,
	String personaId
) {
}
