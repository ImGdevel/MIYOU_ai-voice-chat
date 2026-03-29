package com.miyou.app.application.monitoring.port;

public interface RagQualityMetricsPort {
	void recordMemoryCandidateCount(int count);

	void recordMemoryFilteredCount(int count);

	void recordMemorySimilarityScore(double score);

	void recordMemoryImportanceScore(double importance);

	void recordDocumentRelevanceScore(double score);
}
