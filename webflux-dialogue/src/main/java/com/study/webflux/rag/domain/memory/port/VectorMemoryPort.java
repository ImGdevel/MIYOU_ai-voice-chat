package com.study.webflux.rag.domain.memory.port;

import java.time.Instant;
import java.util.List;

import com.study.webflux.rag.domain.dialogue.model.ConversationSessionId;
import com.study.webflux.rag.domain.memory.model.Memory;
import com.study.webflux.rag.domain.memory.model.MemoryType;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public interface VectorMemoryPort {

	Mono<Memory> upsert(Memory memory, List<Float> embedding);

	Flux<Memory> search(ConversationSessionId sessionId,
		List<Float> queryEmbedding,
		List<MemoryType> types,
		float importanceThreshold,
		int topK);

	Mono<Void> updateImportance(String memoryId,
		float newImportance,
		Instant lastAccessedAt,
		int accessCount);
}
