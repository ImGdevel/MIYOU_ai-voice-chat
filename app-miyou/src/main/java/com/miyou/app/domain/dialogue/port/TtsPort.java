package com.miyou.app.domain.dialogue.port;

import com.miyou.app.domain.voice.model.AudioFormat;
import com.miyou.app.domain.voice.model.Voice;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public interface TtsPort {
	Flux<byte[]> streamSynthesize(String text, AudioFormat format);

	default Flux<byte[]> streamSynthesize(String text) {
		return streamSynthesize(text, null);
	}

	Flux<byte[]> streamSynthesize(String text, AudioFormat format, Voice voice);

	Mono<byte[]> synthesize(String text, AudioFormat format);

	default Mono<byte[]> synthesize(String text) {
		return synthesize(text, null);
	}

	Mono<byte[]> synthesize(String text, AudioFormat format, Voice voice);

	default Mono<Void> prepare() {
		return Mono.empty();
	}
}
