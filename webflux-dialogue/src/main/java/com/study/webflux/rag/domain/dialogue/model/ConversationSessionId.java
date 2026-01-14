package com.study.webflux.rag.domain.dialogue.model;

import java.util.UUID;

public record ConversationSessionId(
	String value
) {
	public ConversationSessionId {
		if (value == null || value.isBlank()) {
			throw new IllegalArgumentException("sessionId cannot be null or blank");
		}
		if (value.length() > 128) {
			throw new IllegalArgumentException("sessionId too long");
		}
	}

	public static ConversationSessionId of(String value) {
		return new ConversationSessionId(value);
	}

	public static ConversationSessionId generate() {
		return new ConversationSessionId(UUID.randomUUID().toString());
	}
}
