package com.miyou.app.infrastructure.inbound.web.dialogue.dto;

public record CreateSessionResponse(
	String sessionId,
	String userId,
	String personaId
) {
}
