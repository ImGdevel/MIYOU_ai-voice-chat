package com.study.webflux.rag.application.dialogue.dto;

public record CreateSessionRequest(
	String userId,
	String personaId
) {
}
