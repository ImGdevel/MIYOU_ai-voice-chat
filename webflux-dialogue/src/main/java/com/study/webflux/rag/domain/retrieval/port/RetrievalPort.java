package com.study.webflux.rag.domain.retrieval.port;

import com.study.webflux.rag.domain.dialogue.model.ConversationSessionId;
import com.study.webflux.rag.domain.memory.model.MemoryRetrievalResult;
import com.study.webflux.rag.domain.retrieval.model.RetrievalContext;
import reactor.core.publisher.Mono;

public interface RetrievalPort {

	Mono<RetrievalContext> retrieve(ConversationSessionId sessionId, String query, int topK);

	Mono<MemoryRetrievalResult> retrieveMemories(ConversationSessionId sessionId,
		String query,
		int topK);
}
