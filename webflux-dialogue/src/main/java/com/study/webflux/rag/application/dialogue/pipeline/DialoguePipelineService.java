package com.study.webflux.rag.application.dialogue.pipeline;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Service;

import com.study.webflux.rag.application.monitoring.aop.MonitoredPipeline;
import com.study.webflux.rag.application.dialogue.pipeline.stage.DialogueInputService;
import com.study.webflux.rag.application.dialogue.pipeline.stage.DialogueLlmStreamService;
import com.study.webflux.rag.application.dialogue.pipeline.stage.DialoguePostProcessingService;
import com.study.webflux.rag.application.dialogue.pipeline.stage.DialogueTtsStreamService;
import com.study.webflux.rag.domain.dialogue.port.DialoguePipelineUseCase;
import com.study.webflux.rag.domain.voice.model.AudioFormat;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Service
@RequiredArgsConstructor
public class DialoguePipelineService implements DialoguePipelineUseCase {

	/**
	 * 리액티브 대화 파이프라인을 총괄합니다. 입력 텍스트를 받아 메모리/검색 컨텍스트를 준비하고, 시스템 프롬프트를 구성한 뒤 LLM 토큰 스트리밍과 TTS 스트리밍까지 이어지는 전체 흐름을 관리합니다.
	 */
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

		Mono<PipelineInputs> inputsMono = inputService.prepareInputs(text).cache();
		Mono<Void> ttsWarmup = ttsStreamService.prepareTtsWarmup();

		Flux<String> llmTokens = llmStreamService.buildLlmTokenStream(inputsMono);
		Flux<String> sentences = ttsStreamService.assembleSentences(llmTokens).cache();
		Flux<byte[]> audioFlux = ttsStreamService.buildAudioStream(sentences,
			ttsWarmup,
			targetFormat);
		Mono<Void> postProcessing = postProcessingService.persistAndExtract(inputsMono, sentences);
		Flux<byte[]> audioStream = ttsStreamService.traceTtsSynthesis(audioFlux);

		return audioStream.concatWith(postProcessing.thenMany(Flux.empty()));
	}

	/**
	 * 텍스트 입력을 받아 오디오 변환 없이 LLM 텍스트 스트림을 반환합니다.
	 */
	@Override
	@MonitoredPipeline
	public Flux<String> executeTextOnly(String text) {
		Mono<PipelineInputs> inputsMono = inputService.prepareInputs(text).cache();

		Flux<String> llmTokens = llmStreamService.buildLlmTokenStream(inputsMono);
		Flux<String> textStream = llmTokens.cache();
		Mono<Void> postProcessing = postProcessingService.persistAndExtractText(inputsMono,
			textStream);

		return textStream.concatWith(postProcessing.thenMany(Flux.empty()));
	}

}
