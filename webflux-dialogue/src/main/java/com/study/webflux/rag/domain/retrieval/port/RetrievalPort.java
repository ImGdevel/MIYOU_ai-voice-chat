package com.study.webflux.rag.domain.retrieval.port;

import com.study.webflux.rag.domain.memory.model.MemoryRetrievalResult;
import com.study.webflux.rag.domain.memory.model.MemorySearchQuery;
import com.study.webflux.rag.domain.retrieval.model.RetrievalContext;
import com.study.webflux.rag.domain.retrieval.model.RetrievalQuery;
import reactor.core.publisher.Mono;

public interface RetrievalPort {

	Mono<RetrievalContext> retrieve(RetrievalQuery query);

	Mono<MemoryRetrievalResult> retrieveMemories(MemorySearchQuery query);
}
