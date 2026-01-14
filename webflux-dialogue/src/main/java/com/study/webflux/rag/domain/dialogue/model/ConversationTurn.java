package com.study.webflux.rag.domain.dialogue.model;

import java.time.Instant;

public record ConversationTurn(
	String id,
	ConversationSessionId sessionId,
	String query,
	String response,
	Instant createdAt
) {
	public ConversationTurn {
		if (sessionId == null) {
			throw new IllegalArgumentException("sessionId cannot be null");
		}
		if (query == null || query.isBlank()) {
			throw new IllegalArgumentException("query cannot be null or blank");
		}
		if (createdAt == null) {
			createdAt = Instant.now();
		}
	}

	public static ConversationTurn create(ConversationSessionId sessionId, String query) {
		return new ConversationTurn(null, sessionId, query, null, Instant.now());
	}

	public static ConversationTurn withId(String id,
		ConversationSessionId sessionId,
		String query,
		String response,
		Instant createdAt) {
		return new ConversationTurn(id, sessionId, query, response, createdAt);
	}

	public ConversationTurn withResponse(String response) {
		return new ConversationTurn(this.id, this.sessionId, this.query, response, this.createdAt);
	}
}
