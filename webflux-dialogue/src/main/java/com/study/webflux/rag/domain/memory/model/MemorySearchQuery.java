package com.study.webflux.rag.domain.memory.model;

import java.util.Objects;

import com.study.webflux.rag.domain.dialogue.model.ConversationSessionId;

/** 세션 기반 메모리 검색 질의입니다. */
public record MemorySearchQuery(
	ConversationSessionId sessionId,
	String query,
	int topK
) {
	public MemorySearchQuery {
		Objects.requireNonNull(sessionId, "sessionId must not be null");
		if (query == null || query.isBlank()) {
			throw new IllegalArgumentException("query must not be blank");
		}
		if (topK <= 0) {
			throw new IllegalArgumentException("topK must be positive");
		}
		query = query.trim();
	}
}
