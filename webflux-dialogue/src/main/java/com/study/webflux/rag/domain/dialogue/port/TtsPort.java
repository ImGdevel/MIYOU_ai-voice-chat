package com.study.webflux.rag.domain.dialogue.port;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public interface TtsPort {
	Flux<byte[]> streamSynthesize(String text);

	Mono<byte[]> synthesize(String text);

	default Mono<Void> prepare() {
		return Mono.empty();
	}
}
