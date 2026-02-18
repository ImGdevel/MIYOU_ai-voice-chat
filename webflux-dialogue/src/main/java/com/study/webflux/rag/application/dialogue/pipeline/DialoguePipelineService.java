package com.study.webflux.rag.application.dialogue.pipeline;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Service;

import com.study.webflux.rag.application.dialogue.pipeline.stage.DialogueInputService;
import com.study.webflux.rag.application.dialogue.pipeline.stage.DialogueLlmStreamService;
import com.study.webflux.rag.application.dialogue.pipeline.stage.DialoguePostProcessingService;
import com.study.webflux.rag.application.dialogue.pipeline.stage.DialogueTtsStreamService;
import com.study.webflux.rag.application.monitoring.aop.MonitoredPipeline;
import com.study.webflux.rag.domain.dialogue.model.ConversationSession;
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

	@Override
	@MonitoredPipeline
	public Flux<byte[]> executeAudioStreaming(ConversationSession session,
		String text,
		AudioFormat format) {
		AudioFormat targetFormat = format != null ? format : defaultAudioFormat;

		Mono<PipelineInputs> inputsMono = inputService.prepareInputs(session, text).cache();
		Mono<Void> ttsWarmup = ttsStreamService.prepareTtsWarmup();

		Flux<String> llmTokens = llmStreamService.buildLlmTokenStream(inputsMono);
		Flux<String> sentences = ttsStreamService.assembleSentences(llmTokens).cache();
		Flux<byte[]> audioFlux = ttsStreamService
			.buildAudioStream(sentences, ttsWarmup, targetFormat, session.personaId());
		Mono<Void> postProcessing = postProcessingService.persistAndExtract(inputsMono, sentences);
		Flux<byte[]> audioStream = ttsStreamService.traceTtsSynthesis(audioFlux);

		return appendPostProcessing(audioStream, postProcessing);
	}

	@Override
	@MonitoredPipeline
	public Flux<String> executeTextOnly(ConversationSession session, String text) {
		Mono<PipelineInputs> inputsMono = inputService.prepareInputs(session, text).cache();

		Flux<String> llmTokens = llmStreamService.buildLlmTokenStream(inputsMono);
		Flux<String> textStream = llmTokens.cache();
		Mono<Void> postProcessing = postProcessingService.persistAndExtractText(inputsMono,
			textStream);

		return appendPostProcessing(textStream, postProcessing);
	}

	private <T> Flux<T> appendPostProcessing(Flux<T> mainStream, Mono<Void> postProcessing) {
		return mainStream.concatWith(postProcessing.thenMany(Flux.empty()));
	}
}
