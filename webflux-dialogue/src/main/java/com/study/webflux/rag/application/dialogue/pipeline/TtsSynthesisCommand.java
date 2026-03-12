package com.study.webflux.rag.application.dialogue.pipeline;

import java.util.Objects;

import com.study.webflux.rag.domain.dialogue.model.PersonaId;
import com.study.webflux.rag.domain.voice.model.AudioFormat;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/** TTS 합성 입력입니다. */
public record TtsSynthesisCommand(
	Flux<String> sentences,
	Mono<Void> ttsWarmup,
	AudioFormat targetFormat,
	PersonaId personaId
) {
	public TtsSynthesisCommand {
		Objects.requireNonNull(sentences, "sentences must not be null");
		Objects.requireNonNull(ttsWarmup, "ttsWarmup must not be null");
		Objects.requireNonNull(targetFormat, "targetFormat must not be null");
		Objects.requireNonNull(personaId, "personaId must not be null");
	}
}
