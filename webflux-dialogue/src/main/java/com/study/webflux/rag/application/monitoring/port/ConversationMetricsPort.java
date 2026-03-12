package com.study.webflux.rag.application.monitoring.port;

public interface ConversationMetricsPort {
	void recordConversationIncrement();

	void recordConversationReset();

	void recordQueryLength(int length);

	void recordResponseLength(int length);

	void recordConversationCount(long count);
}
