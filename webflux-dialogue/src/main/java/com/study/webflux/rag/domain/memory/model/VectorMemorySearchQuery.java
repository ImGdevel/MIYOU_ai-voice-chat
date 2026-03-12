package com.study.webflux.rag.domain.memory.model;

import java.util.List;
import java.util.Objects;

import com.study.webflux.rag.domain.dialogue.model.ConversationSessionId;

/** 벡터 메모리 저장소 조회 질의입니다. */
public record VectorMemorySearchQuery(
	ConversationSessionId sessionId,
	List<Float> queryEmbedding,
	List<MemoryType> types,
	float importanceThreshold,
	int topK
) {
	public VectorMemorySearchQuery {
		Objects.requireNonNull(sessionId, "sessionId must not be null");
		queryEmbedding = List.copyOf(Objects.requireNonNull(queryEmbedding,
			"queryEmbedding must not be null"));
		types = types == null ? List.of() : List.copyOf(types);
		if (topK <= 0) {
			throw new IllegalArgumentException("topK must be positive");
		}
	}
}
