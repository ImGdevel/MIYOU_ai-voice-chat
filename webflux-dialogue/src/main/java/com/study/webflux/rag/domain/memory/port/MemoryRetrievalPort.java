package com.study.webflux.rag.domain.memory.port;

import com.study.webflux.rag.domain.memory.model.MemoryRetrievalResult;
import com.study.webflux.rag.domain.memory.model.MemorySearchQuery;
import reactor.core.publisher.Mono;

public interface MemoryRetrievalPort {
	Mono<MemoryRetrievalResult> retrieveMemories(MemorySearchQuery query);
}
