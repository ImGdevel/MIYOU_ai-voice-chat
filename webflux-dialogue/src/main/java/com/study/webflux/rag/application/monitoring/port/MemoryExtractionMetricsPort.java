package com.study.webflux.rag.application.monitoring.port;

public interface MemoryExtractionMetricsPort {
	void recordExtractionTriggered();

	void recordExtractionSuccess(int count);

	void recordExtractionFailure();

	void recordExtractedMemoryType(String type, int count);

	void recordExtractedImportance(double importance);
}
