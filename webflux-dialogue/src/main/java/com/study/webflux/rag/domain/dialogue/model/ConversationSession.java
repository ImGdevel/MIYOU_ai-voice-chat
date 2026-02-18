package com.study.webflux.rag.domain.dialogue.model;

import java.time.Instant;

public record ConversationSession(
	ConversationSessionId sessionId,
	PersonaId personaId,
	UserId userId,
	Instant createdAt,
	Instant deletedAt
) {
	public ConversationSession {
		if (sessionId == null) {
			throw new IllegalArgumentException("sessionId cannot be null");
		}
		if (personaId == null) {
			personaId = PersonaId.defaultPersona();
		}
		if (userId == null) {
			throw new IllegalArgumentException("userId cannot be null");
		}
		if (createdAt == null) {
			createdAt = Instant.now();
		}
	}

	public static ConversationSession create(PersonaId personaId, UserId userId) {
		return new ConversationSession(
			ConversationSessionId.generate(),
			personaId,
			userId,
			Instant.now(),
			null);
	}

	public boolean isActive() {
		return deletedAt == null;
	}

	public ConversationSession softDelete() {
		return new ConversationSession(sessionId, personaId, userId, createdAt, Instant.now());
	}
}
