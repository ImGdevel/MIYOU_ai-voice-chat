package com.study.webflux.rag.infrastructure.inbound.web.dialogue.dto;

public record CreateSessionRequest(
	String userId,
	String personaId
) {
}
