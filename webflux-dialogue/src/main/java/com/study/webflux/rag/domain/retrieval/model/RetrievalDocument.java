package com.study.webflux.rag.domain.retrieval.model;

import java.util.Map;

public record RetrievalDocument(
	String content,
	com.study.webflux.rag.domain.retrieval.model.SimilarityScore score,
	Map<String, Object> metadata
) {
	public RetrievalDocument {
		if (content == null || content.isBlank()) {
			throw new IllegalArgumentException("content cannot be null or blank");
		}
		if (score == null) {
			throw new IllegalArgumentException("score cannot be null");
		}
		if (metadata == null) {
			metadata = Map.of();
		}
	}

	public static RetrievalDocument of(String content, int score) {
		return new RetrievalDocument(content,
			com.study.webflux.rag.domain.retrieval.model.SimilarityScore.of(score), Map.of());
	}

	public static RetrievalDocument withMetadata(String content,
		int score,
		Map<String, Object> metadata) {
		return new RetrievalDocument(content,
			com.study.webflux.rag.domain.retrieval.model.SimilarityScore.of(score), metadata);
	}
}
