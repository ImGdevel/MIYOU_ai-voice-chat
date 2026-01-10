package com.study.webflux.rag.domain.dialogue.model;

import java.util.UUID;

public record UserId(
	String value
) {
	public UserId {
		if (value == null || value.isBlank()) {
			throw new IllegalArgumentException("userId cannot be null or blank");
		}
		if (value.length() > 128) {
			throw new IllegalArgumentException("userId too long");
		}
	}

	public static UserId of(String value) {
		return new UserId(value);
	}

	public static UserId generate() {
		return new UserId(UUID.randomUUID().toString());
	}
}
