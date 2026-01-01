package com.study.webflux.rag.application.dialogue.service;

import lombok.extern.slf4j.Slf4j;

import org.springframework.stereotype.Service;

import com.study.webflux.rag.application.monitoring.monitor.DialoguePipelineTracker;
import com.study.webflux.rag.application.monitoring.service.PipelineTracer;
import com.study.webflux.rag.domain.dialogue.port.TtsPort;
import com.study.webflux.rag.domain.dialogue.service.SentenceAssembler;
import com.study.webflux.rag.domain.monitoring.model.DialoguePipelineStage;
import com.study.webflux.rag.domain.voice.model.AudioFormat;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

@Slf4j
@Service
public class DialogueTtsStreamService {

	private final TtsPort ttsPort;
	private final SentenceAssembler sentenceAssembler;
	private final PipelineTracer pipelineTracer;

	public DialogueTtsStreamService(TtsPort ttsPort,
		SentenceAssembler sentenceAssembler,
		PipelineTracer pipelineTracer) {
		this.ttsPort = ttsPort;
		this.sentenceAssembler = sentenceAssembler;
		this.pipelineTracer = pipelineTracer;
	}

	public Mono<Void> prepareTtsWarmup(DialoguePipelineTracker tracker) {
		return pipelineTracer.traceTtsPreparation(tracker,
			() -> ttsPort.prepare().doOnError(error -> log
				.warn("파이프라인 {}의 TTS 준비 실패: {}", tracker.pipelineId(), error.getMessage()))
				.onErrorResume(error -> Mono.empty()))
			.cache();
	}

	public Flux<String> assembleSentences(DialoguePipelineTracker tracker,
		Flux<String> llmTokens) {
		return pipelineTracer.traceSentenceAssembly(tracker,
			() -> sentenceAssembler.assemble(llmTokens),
			sentence -> {
				tracker.recordLlmOutput(sentence);
				log.debug("Sentence: [{}]", sentence);
			});
	}

	public Flux<byte[]> buildAudioStream(Flux<String> sentences,
		Mono<Void> ttsWarmup,
		AudioFormat targetFormat) {
		return sentences.publishOn(Schedulers.boundedElastic())
			.concatMap(sentence -> ttsWarmup.thenMany(ttsPort.streamSynthesize(sentence,
				targetFormat)));
	}

	public Flux<byte[]> traceTtsSynthesis(DialoguePipelineTracker tracker, Flux<byte[]> audioFlux) {
		return pipelineTracer
			.traceTtsSynthesis(tracker, () -> audioFlux, () -> {
				tracker
					.incrementStageCounter(DialoguePipelineStage.TTS_SYNTHESIS, "audioChunks", 1);
				tracker.markResponseEmission();
			});
	}
}
