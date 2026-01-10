package com.study.webflux.rag.domain.memory.model;

import com.study.webflux.rag.domain.dialogue.model.UserId;

public record ExtractedMemory(
	UserId userId,
	MemoryType type,
	String content,
	float importance,
	String reasoning
) {
	public ExtractedMemory {
		if (userId == null) {
			throw new IllegalArgumentException("userId cannot be null");
		}
		if (type == null) {
			throw new IllegalArgumentException("type cannot be null");
		}
		if (content == null || content.isBlank()) {
			throw new IllegalArgumentException("content cannot be null or blank");
		}
		if (importance < 0.0f || importance > 1.0f) {
			throw new IllegalArgumentException("importance must be between 0.0 and 1.0");
		}
	}

	public Memory toMemory() {
		return Memory.create(userId, type, content, importance);
	}
}
