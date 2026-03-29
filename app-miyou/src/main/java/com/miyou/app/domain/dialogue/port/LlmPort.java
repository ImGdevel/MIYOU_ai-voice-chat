package com.miyou.app.domain.dialogue.port;

import com.miyou.app.domain.dialogue.model.CompletionRequest;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public interface LlmPort {
	Flux<String> streamCompletion(CompletionRequest request);

	Mono<String> complete(CompletionRequest request);
}
