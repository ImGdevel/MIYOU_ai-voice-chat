package com.study.webflux.rag.domain.retrieval.model;

import java.util.Objects;

import com.study.webflux.rag.domain.dialogue.model.ConversationSessionId;

/** 검색 컨텍스트 조회 질의입니다. */
public record RetrievalQuery(
	ConversationSessionId sessionId,
	String query,
	int topK
) {
	public RetrievalQuery {
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
