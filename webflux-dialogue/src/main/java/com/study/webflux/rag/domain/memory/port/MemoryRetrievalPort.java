package com.study.webflux.rag.domain.memory.port;

import com.study.webflux.rag.domain.dialogue.model.ConversationSessionId;
import com.study.webflux.rag.domain.memory.model.MemoryRetrievalResult;
import reactor.core.publisher.Mono;

public interface MemoryRetrievalPort {
	Mono<MemoryRetrievalResult> retrieveMemories(ConversationSessionId sessionId,
		String query,
		int topK);
}
