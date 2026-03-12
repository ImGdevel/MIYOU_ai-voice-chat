package com.study.webflux.rag.domain.memory.port;

import java.util.List;

import com.study.webflux.rag.domain.memory.model.Memory;
import com.study.webflux.rag.domain.memory.model.MemoryImportanceUpdateCommand;
import com.study.webflux.rag.domain.memory.model.VectorMemorySearchQuery;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public interface VectorMemoryPort {

	Mono<Memory> upsert(Memory memory, List<Float> embedding);

	Flux<Memory> search(VectorMemorySearchQuery query);

	Mono<Void> updateImportance(MemoryImportanceUpdateCommand command);
}
