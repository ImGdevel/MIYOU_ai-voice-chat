package com.study.webflux.rag.application.dialogue.service;

import java.util.Base64;

import org.springframework.stereotype.Service;

import com.study.webflux.rag.application.monitoring.monitor.DialoguePipelineMonitor;
import com.study.webflux.rag.application.monitoring.monitor.DialoguePipelineTracker;
import com.study.webflux.rag.domain.dialogue.port.DialoguePipelineUseCase;
import com.study.webflux.rag.domain.voice.model.AudioFormat;
import com.study.webflux.rag.infrastructure.dialogue.config.properties.RagDialogueProperties;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Service
public class DialoguePipelineService implements DialoguePipelineUseCase {

	/**
	 * 리액티브 대화 파이프라인을 총괄합니다. 입력 텍스트를 받아 메모리/검색 컨텍스트를 준비하고, 시스템 프롬프트를 구성한 뒤 LLM 토큰 스트리밍과 TTS 스트리밍까지 이어지는 전체 흐름을 관리합니다.
	 */
	private final DialoguePipelineMonitor pipelineMonitor;
	private final DialogueInputService inputService;
	private final DialogueLlmStreamService llmStreamService;
	private final DialogueTtsStreamService ttsStreamService;
	private final DialoguePostProcessingService postProcessingService;
	private final AudioFormat defaultAudioFormat;

	public DialoguePipelineService(DialoguePipelineMonitor pipelineMonitor,
		DialogueInputService inputService,
		DialogueLlmStreamService llmStreamService,
		DialogueTtsStreamService ttsStreamService,
		DialoguePostProcessingService postProcessingService,
		RagDialogueProperties properties) {
		this.pipelineMonitor = pipelineMonitor;
		this.inputService = inputService;
		this.llmStreamService = llmStreamService;
		this.ttsStreamService = ttsStreamService;
		this.postProcessingService = postProcessingService;
		this.defaultAudioFormat = parseAudioFormat(properties.getSupertone().getOutputFormat());
	}

	/**
	 * 텍스트 입력을 받아 오디오 바이트 스트림을 반환합니다. 응답 생성이 완료된 뒤에만 쿼리를 저장해 중간 실패 시 저장을 피합니다.
	 */
	@Override
	public Flux<byte[]> executeAudioStreaming(String text, AudioFormat format) {
		AudioFormat targetFormat = format != null ? format : defaultAudioFormat;
		DialoguePipelineTracker tracker = pipelineMonitor.create(text);

		Mono<PipelineInputs> inputsMono = inputService.prepareInputs(text, tracker).cache();
		Mono<Void> ttsWarmup = ttsStreamService.prepareTtsWarmup(tracker);

		Flux<String> llmTokens = buildLlmTokenStream(tracker, inputsMono);
		Flux<String> sentences = buildSentenceStream(tracker, llmTokens);
		Flux<byte[]> audioFlux = ttsStreamService.buildAudioStream(sentences,
			ttsWarmup,
			targetFormat);
		Mono<Void> postProcessing = postProcessingService.persistAndExtract(inputsMono,
			sentences,
			tracker);
		Flux<byte[]> audioStream = ttsStreamService.traceTtsSynthesis(tracker, audioFlux);

		return tracker
			.attachLifecycle(audioStream.concatWith(postProcessing.thenMany(Flux.empty())));
	}

	@Override
	public Flux<String> executeTextOnly(String text) {
		DialoguePipelineTracker tracker = pipelineMonitor.create(text);

		Mono<PipelineInputs> inputsMono = inputService.prepareInputs(text, tracker).cache();

		Flux<String> llmTokens = buildLlmTokenStream(tracker, inputsMono);
		Flux<String> textStream = buildTextStream(llmTokens);
		Mono<Void> postProcessing = postProcessingService.persistAndExtractText(inputsMono,
			textStream,
			tracker);

		return tracker
			.attachLifecycle(textStream.concatWith(postProcessing.thenMany(Flux.empty())));
	}

	@Override
	public Flux<String> executeStreaming(String text, AudioFormat format) {
		AudioFormat targetFormat = format != null ? format : defaultAudioFormat;
		return executeAudioStreaming(text, targetFormat).map(bytes -> Base64.getEncoder()
			.encodeToString(bytes));
	}

	// ===================================================================

	private Flux<String> buildLlmTokenStream(DialoguePipelineTracker tracker,
		Mono<PipelineInputs> inputsMono) {
		return llmStreamService.buildLlmTokenStream(tracker, inputsMono);
	}

	private Flux<String> buildSentenceStream(DialoguePipelineTracker tracker,
		Flux<String> llmTokens) {
		return ttsStreamService.assembleSentences(tracker, llmTokens).cache();
	}

	private Flux<String> buildTextStream(Flux<String> llmTokens) {
		return llmTokens.cache();
	}

	private static AudioFormat parseAudioFormat(String configuredFormat) {
		try {
			return AudioFormat.fromString(configuredFormat);
		} catch (IllegalArgumentException e) {
			throw new IllegalArgumentException(
				"Invalid audio format configured for 'supertone.output-format': "
					+ configuredFormat,
				e);
		}
	}

}
