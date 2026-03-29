package com.miyou.app.domain.retrieval.port;

import com.miyou.app.domain.dialogue.model.ConversationSessionId;
import com.miyou.app.domain.memory.model.MemoryRetrievalResult;
import com.miyou.app.domain.retrieval.model.RetrievalContext;
import reactor.core.publisher.Mono;

public interface RetrievalPort {

	Mono<RetrievalContext> retrieve(ConversationSessionId sessionId, String query, int topK);

	Mono<MemoryRetrievalResult> retrieveMemories(ConversationSessionId sessionId,
		String query,
		int topK);
}
