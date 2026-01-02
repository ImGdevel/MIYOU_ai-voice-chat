package com.study.webflux.rag.application.dialogue.pipeline;

import java.util.Arrays;

import com.study.webflux.rag.application.dialogue.pipeline.stage.DialogueInputService;
import com.study.webflux.rag.application.dialogue.pipeline.stage.DialogueLlmStreamService;
import com.study.webflux.rag.application.dialogue.pipeline.stage.DialoguePostProcessingService;
import com.study.webflux.rag.application.dialogue.pipeline.stage.DialogueTtsStreamService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DialoguePipelineServiceTest {

	@Mock
	private DialogueInputService inputService;

	@Mock
	private DialogueLlmStreamService llmStreamService;

	@Mock
	private DialogueTtsStreamService ttsStreamService;

	@Mock
	private DialoguePostProcessingService postProcessingService;

	private DialoguePipelineService service;

	@BeforeEach
	void setUp() {
		service = new DialoguePipelineService(inputService,
			llmStreamService,
			ttsStreamService,
			postProcessingService);
	}

	@Test
	void executeAudioStreaming_shouldUseDelegatedFlows() {
		String testText = "test";
		PipelineInputs inputs = new PipelineInputs(null, null, null,
			com.study.webflux.rag.domain.dialogue.model.ConversationTurn.create(testText));

		when(inputService.prepareInputs(eq(testText))).thenReturn(Mono.just(inputs));
		when(ttsStreamService.prepareTtsWarmup()).thenReturn(Mono.empty());
		when(llmStreamService.buildLlmTokenStream(any())).thenReturn(Flux.just("a", "b"));
		when(ttsStreamService.assembleSentences(any())).thenReturn(Flux.just("ab"));
		when(ttsStreamService.buildAudioStream(any(), any(), any()))
			.thenReturn(Flux.just("audio".getBytes()));
		when(ttsStreamService.traceTtsSynthesis(any()))
			.thenReturn(Flux.just("audio".getBytes()));
		when(postProcessingService.persistAndExtract(any(), any())).thenReturn(Mono.empty());

		StepVerifier.create(service.executeAudioStreaming(testText))
			.expectNextMatches(bytes -> Arrays.equals(bytes, "audio".getBytes()))
			.verifyComplete();

		verify(inputService).prepareInputs(testText);
		verify(postProcessingService).persistAndExtract(any(), any());
	}

	@Test
	void executeTextOnly_shouldDelegateToUseCase() {
		String testText = "hello";
		PipelineInputs inputs = new PipelineInputs(null, null, null,
			com.study.webflux.rag.domain.dialogue.model.ConversationTurn.create(testText));

		when(inputService.prepareInputs(eq(testText))).thenReturn(Mono.just(inputs));
		when(llmStreamService.buildLlmTokenStream(any())).thenReturn(Flux.just("hi"));
		when(postProcessingService.persistAndExtractText(any(), any())).thenReturn(Mono.empty());

		StepVerifier.create(service.executeTextOnly(testText)).expectNext("hi").verifyComplete();

		verify(inputService, times(1)).prepareInputs(testText);
	}
}
