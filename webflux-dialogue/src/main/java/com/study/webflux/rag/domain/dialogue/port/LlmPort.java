package com.study.webflux.rag.domain.dialogue.port;

import com.study.webflux.rag.domain.dialogue.model.CompletionRequest;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public interface LlmPort {
	Flux<String> streamCompletion(CompletionRequest request);

	Mono<String> complete(CompletionRequest request);
}
