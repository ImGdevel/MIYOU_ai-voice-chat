package com.study.webflux.rag.domain.dialogue.model;

import java.time.Instant;

public record ConversationTurn(
	String id,
	UserId userId,
	String query,
	String response,
	Instant createdAt
) {
	public ConversationTurn {
		if (userId == null) {
			throw new IllegalArgumentException("userId cannot be null");
		}
		if (query == null || query.isBlank()) {
			throw new IllegalArgumentException("query cannot be null or blank");
		}
		if (createdAt == null) {
			createdAt = Instant.now();
		}
	}

	public static ConversationTurn create(UserId userId, String query) {
		return new ConversationTurn(null, userId, query, null, Instant.now());
	}

	public static ConversationTurn create(String query) {
		return create(UserId.generate(), query);
	}

	public static ConversationTurn withId(String id,
		UserId userId,
		String query,
		String response,
		Instant createdAt) {
		return new ConversationTurn(id, userId, query, response, createdAt);
	}

	public static ConversationTurn withId(String id,
		String query,
		String response,
		Instant createdAt) {
		return withId(id, UserId.generate(), query, response, createdAt);
	}

	public ConversationTurn withResponse(String response) {
		return new ConversationTurn(this.id, this.userId, this.query, response, this.createdAt);
	}
}
