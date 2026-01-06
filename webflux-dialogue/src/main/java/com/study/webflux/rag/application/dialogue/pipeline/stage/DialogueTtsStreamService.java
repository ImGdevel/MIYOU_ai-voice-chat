package com.study.webflux.rag.application.dialogue.pipeline.stage;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.stereotype.Service;

import com.study.webflux.rag.application.monitoring.context.PipelineContext;
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
@RequiredArgsConstructor
public class DialogueTtsStreamService {

	private final TtsPort ttsPort;
	private final SentenceAssembler sentenceAssembler;
	private final PipelineTracer pipelineTracer;

	/**
	 * 현재 파이프라인 요청 범위에서 TTS 준비 작업을 최대 1회 수행하도록 warm-up Mono를 생성합니다.
	 */
	public Mono<Void> prepareTtsWarmup() {
		return Mono.deferContextual(contextView -> {
			var tracker = PipelineContext.findTracker(contextView);
			String pipelineId = tracker != null ? tracker.pipelineId() : "unknown";
			return pipelineTracer.traceTtsPreparation(
				() -> ttsPort.prepare().doOnError(error -> log
					.warn("파이프라인 {}의 TTS 준비 실패: {}", pipelineId, error.getMessage()))
					.onErrorResume(error -> Mono.empty()));
		}).cache();
	}

	/**
	 * LLM 토큰 스트림을 문장 단위 스트림으로 조립합니다.
	 */
	public Flux<String> assembleSentences(Flux<String> llmTokens) {
		return pipelineTracer.traceSentenceAssembly(
			() -> sentenceAssembler.assemble(llmTokens),
			(tracker, sentence) -> {
				tracker.recordLlmOutput(sentence);
				log.debug("Sentence: [{}]", sentence);
			});
	}

	/**
	 * 문장 스트림을 순차적으로 TTS 합성해 오디오 청크 스트림으로 변환합니다.
	 */
	public Flux<byte[]> buildAudioStream(Flux<String> sentences,
		Mono<Void> ttsWarmup,
		AudioFormat targetFormat) {
		return sentences.publishOn(Schedulers.boundedElastic())
			.concatMap(sentence -> ttsWarmup.thenMany(ttsPort.streamSynthesize(sentence,
				targetFormat)));
	}

	/**
	 * TTS 합성 스트림을 모니터링하며 오디오 청크 수와 응답 시점을 기록합니다.
	 */
	public Flux<byte[]> traceTtsSynthesis(Flux<byte[]> audioFlux) {
		return pipelineTracer
			.traceTtsSynthesis(() -> audioFlux, (tracker, chunk) -> {
				tracker
					.incrementStageCounter(DialoguePipelineStage.TTS_SYNTHESIS, "audioChunks", 1);
				tracker.markResponseEmission();
			});
	}
}
