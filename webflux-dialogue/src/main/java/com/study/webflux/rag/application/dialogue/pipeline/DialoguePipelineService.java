package com.study.webflux.rag.application.dialogue.pipeline;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Service;

import com.study.webflux.rag.application.dialogue.pipeline.stage.DialogueInputService;
import com.study.webflux.rag.application.dialogue.pipeline.stage.DialogueLlmStreamService;
import com.study.webflux.rag.application.dialogue.pipeline.stage.DialoguePostProcessingService;
import com.study.webflux.rag.application.dialogue.pipeline.stage.DialogueTtsStreamService;
import com.study.webflux.rag.application.monitoring.aop.MonitoredPipeline;
import com.study.webflux.rag.domain.dialogue.port.DialoguePipelineUseCase;
import com.study.webflux.rag.domain.voice.model.AudioFormat;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Service
@RequiredArgsConstructor
public class DialoguePipelineService implements DialoguePipelineUseCase {

	private final DialogueInputService inputService;
	private final DialogueLlmStreamService llmStreamService;
	private final DialogueTtsStreamService ttsStreamService;
	private final DialoguePostProcessingService postProcessingService;
	private final AudioFormat defaultAudioFormat = AudioFormat.MP3;

	/**
	 * 텍스트 입력을 오디오 스트림으로 변환합니다. LLM → 문장 조립 → TTS → 저장/메모리 추출까지 한 번에 실행합니다.
	 */
	@Override
	@MonitoredPipeline
	public Flux<byte[]> executeAudioStreaming(String text, AudioFormat format) {
		AudioFormat targetFormat = format != null ? format : defaultAudioFormat;

		Mono<PipelineInputs> inputsMono = prepareCachedInputs(text);
		Mono<Void> ttsWarmup = ttsStreamService.prepareTtsWarmup();

		Flux<String> llmTokens = llmStreamService.buildLlmTokenStream(inputsMono);
		Flux<String> sentences = ttsStreamService.assembleSentences(llmTokens).cache();
		Flux<byte[]> audioFlux = ttsStreamService
			.buildAudioStream(sentences, ttsWarmup, targetFormat);
		Mono<Void> postProcessing = postProcessingService.persistAndExtract(inputsMono, sentences);
		Flux<byte[]> audioStream = ttsStreamService.traceTtsSynthesis(audioFlux);

		return appendPostProcessing(audioStream, postProcessing);
	}

	/**
	 * 텍스트 입력을 받아 오디오 변환 없이 LLM 텍스트 스트림을 반환합니다.
	 */
	@Override
	@MonitoredPipeline
	public Flux<String> executeTextOnly(String text) {
		Mono<PipelineInputs> inputsMono = prepareCachedInputs(text);

		Flux<String> llmTokens = llmStreamService.buildLlmTokenStream(inputsMono);
		Flux<String> textStream = llmTokens.cache();
		Mono<Void> postProcessing = postProcessingService.persistAndExtractText(inputsMono,
			textStream);

		return appendPostProcessing(textStream, postProcessing);
	}

	/**
	 * 입력 텍스트로부터 파이프라인 공통 입력 객체를 생성하고 같은 요청 내 재사용을 위해 캐시합니다.
	 */
	private Mono<PipelineInputs> prepareCachedInputs(String text) {
		return inputService.prepareInputs(text).cache();
	}

	/**
	 * 메인 스트림 완료 후 후처리 Mono를 이어붙여 저장/메모리추출이 누락되지 않도록 보장합니다.
	 */
	private <T> Flux<T> appendPostProcessing(Flux<T> mainStream, Mono<Void> postProcessing) {
		return mainStream.concatWith(postProcessing.thenMany(Flux.empty()));
	}
}
