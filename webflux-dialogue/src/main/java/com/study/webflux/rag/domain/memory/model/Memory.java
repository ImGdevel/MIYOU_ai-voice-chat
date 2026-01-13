package com.study.webflux.rag.domain.memory.model;

import java.time.Instant;

import com.study.webflux.rag.domain.dialogue.model.PersonaId;
import com.study.webflux.rag.domain.dialogue.model.UserId;

public record Memory(
	String id,
	PersonaId personaId,
	UserId userId,
	MemoryType type,
	String content,
	Float importance,
	Instant createdAt,
	Instant lastAccessedAt,
	Integer accessCount
) {
	public Memory {
		if (personaId == null) {
			personaId = PersonaId.defaultPersona();
		}
		if (userId == null) {
			throw new IllegalArgumentException("userId cannot be null");
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

	public static Memory create(PersonaId personaId,
		UserId userId,
		MemoryType type,
		String content,
		float importance) {
		Instant now = Instant.now();
		return new Memory(null, personaId, userId, type, content, importance, now, now, 0);
	}

	public static Memory create(UserId userId, MemoryType type, String content, float importance) {
		return create(PersonaId.defaultPersona(), userId, type, content, importance);
	}

	public Memory withId(String id) {
		return new Memory(id, personaId, userId, type, content, importance, createdAt,
			lastAccessedAt,
			accessCount);
	}

	public Memory withAccess(float importanceBoost) {
		float currentImportance = importance != null ? importance : 0.0f;
		float newImportance = Math.min(1.0f, currentImportance + importanceBoost);
		int currentAccessCount = accessCount != null ? accessCount : 0;
		return new Memory(id, personaId, userId, type, content, newImportance, createdAt,
			Instant.now(),
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
