package com.study.webflux.rag.domain.memory.model;

import java.time.Instant;

import com.study.webflux.rag.domain.dialogue.model.ConversationSessionId;

public record Memory(
	String id,
	ConversationSessionId sessionId,
	MemoryType type,
	String content,
	Float importance,
	Instant createdAt,
	Instant lastAccessedAt,
	Integer accessCount
) {
	public Memory {
		if (sessionId == null) {
			throw new IllegalArgumentException("sessionId cannot be null");
		}
		if (type == null) {
			throw new IllegalArgumentException("type cannot be null");
		}
		if (content == null || content.isBlank()) {
			throw new IllegalArgumentException("content cannot be null or blank");
		}
		if (importance != null && (importance < 0.0f || importance > 1.0f)) {
			throw new IllegalArgumentException("importance must be between 0.0 and 1.0");
		}
	}

	public static Memory create(ConversationSessionId sessionId,
		MemoryType type,
		String content,
		float importance) {
		Instant now = Instant.now();
		return new Memory(null, sessionId, type, content, importance, now, now, 0);
	}

	public Memory withId(String id) {
		return new Memory(id, sessionId, type, content, importance, createdAt, lastAccessedAt,
			accessCount);
	}

	public Memory withAccess(float importanceBoost) {
		float currentImportance = importance != null ? importance : 0.0f;
		float newImportance = Math.min(1.0f, currentImportance + importanceBoost);
		int currentAccessCount = accessCount != null ? accessCount : 0;
		return new Memory(id, sessionId, type, content, newImportance, createdAt, Instant.now(),
			currentAccessCount + 1);
	}

	public float calculateRankedScore(float recencyWeight) {
		float baseImportance = importance != null ? importance : 0.5f;

		if (lastAccessedAt == null) {
			return baseImportance;
		}

		long hoursSinceAccess = (Instant.now().getEpochSecond() - lastAccessedAt.getEpochSecond())
			/ 3600;
		float recencyFactor = (float) Math.exp(-recencyWeight * hoursSinceAccess / 24.0);
		return baseImportance * recencyFactor;
	}
}
