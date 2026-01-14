package com.study.webflux.rag.application.dialogue.pipeline;

import java.util.Arrays;

import com.study.webflux.rag.application.dialogue.pipeline.stage.DialogueInputService;
import com.study.webflux.rag.application.dialogue.pipeline.stage.DialogueLlmStreamService;
import com.study.webflux.rag.application.dialogue.pipeline.stage.DialoguePostProcessingService;
import com.study.webflux.rag.application.dialogue.pipeline.stage.DialogueTtsStreamService;
import com.study.webflux.rag.domain.dialogue.model.ConversationSession;
import com.study.webflux.rag.domain.dialogue.model.ConversationTurn;
import com.study.webflux.rag.domain.voice.model.AudioFormat;
import com.study.webflux.rag.fixture.ConversationSessionFixture;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
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
	@DisplayName("오디오 스트리밍 실행 시 전체 파이프라인 스테이지를 순차 호출한다")
	void executeAudioStreaming_shouldUseDelegatedFlows() {
		ConversationSession session = ConversationSessionFixture.create();
		String testText = "test";
		PipelineInputs inputs = new PipelineInputs(session, null, null, null,
			ConversationTurn.create(session.sessionId(), testText));

		when(inputService.prepareInputs(eq(session), eq(testText))).thenReturn(Mono.just(inputs));
		when(ttsStreamService.prepareTtsWarmup()).thenReturn(Mono.empty());
		when(llmStreamService.buildLlmTokenStream(any())).thenReturn(Flux.just("a", "b"));
		when(ttsStreamService.assembleSentences(any())).thenReturn(Flux.just("ab"));
		when(ttsStreamService.buildAudioStream(any(), any(), any(), any()))
			.thenReturn(Flux.just("audio".getBytes()));
		when(ttsStreamService.traceTtsSynthesis(any()))
			.thenReturn(Flux.just("audio".getBytes()));
		when(postProcessingService.persistAndExtract(any(), any())).thenReturn(Mono.empty());

		StepVerifier.create(service.executeAudioStreaming(session, testText, AudioFormat.MP3))
			.expectNextMatches(bytes -> Arrays.equals(bytes, "audio".getBytes()))
			.verifyComplete();

		verify(inputService).prepareInputs(session, testText);
		verify(postProcessingService).persistAndExtract(any(), any());
	}

	@Test
	@DisplayName("텍스트 전용 실행 시 LLM 토큰 스트림을 반환한다")
	void executeTextOnly_shouldDelegateToUseCase() {
		ConversationSession session = ConversationSessionFixture.create();
		String testText = "hello";
		PipelineInputs inputs = new PipelineInputs(session, null, null, null,
			ConversationTurn.create(session.sessionId(), testText));

		when(inputService.prepareInputs(eq(session), eq(testText))).thenReturn(Mono.just(inputs));
		when(llmStreamService.buildLlmTokenStream(any())).thenReturn(Flux.just("hi"));
		when(postProcessingService.persistAndExtractText(any(), any())).thenReturn(Mono.empty());

		StepVerifier.create(service.executeTextOnly(session, testText)).expectNext("hi")
			.verifyComplete();

		verify(inputService, times(1)).prepareInputs(session, testText);
	}
}
