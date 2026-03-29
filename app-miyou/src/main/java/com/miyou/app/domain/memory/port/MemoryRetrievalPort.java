package com.miyou.app.domain.memory.port;

import com.miyou.app.domain.dialogue.model.ConversationSessionId;
import com.miyou.app.domain.memory.model.MemoryRetrievalResult;
import reactor.core.publisher.Mono;

public interface MemoryRetrievalPort {
	Mono<MemoryRetrievalResult> retrieveMemories(ConversationSessionId sessionId,
		String query,
		int topK);
}
