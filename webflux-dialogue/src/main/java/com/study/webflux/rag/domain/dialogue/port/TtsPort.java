package com.study.webflux.rag.domain.dialogue.port;

import com.study.webflux.rag.domain.voice.model.AudioFormat;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public interface TtsPort {
	Flux<byte[]> streamSynthesize(String text, AudioFormat format);

	default Flux<byte[]> streamSynthesize(String text) {
		return streamSynthesize(text, null);
	}

	Mono<byte[]> synthesize(String text, AudioFormat format);

	default Mono<byte[]> synthesize(String text) {
		return synthesize(text, null);
	}

	default Mono<Void> prepare() {
		return Mono.empty();
	}
}
