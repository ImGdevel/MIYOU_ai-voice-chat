package com.miyou.app.application.dialogue.pipeline

import com.miyou.app.application.dialogue.pipeline.stage.DialogueInputService
import com.miyou.app.application.dialogue.pipeline.stage.DialogueLlmStreamService
import com.miyou.app.application.dialogue.pipeline.stage.DialoguePostProcessingService
import com.miyou.app.application.dialogue.pipeline.stage.DialogueTtsStreamService
import com.miyou.app.domain.dialogue.model.ConversationTurn
import com.miyou.app.domain.voice.model.AudioFormat
import com.miyou.app.fixture.ConversationSessionFixture
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.ArgumentMatchers.any
import org.mockito.ArgumentMatchers.eq
import org.mockito.Mock
import org.mockito.Mockito.times
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
import org.mockito.junit.jupiter.MockitoExtension
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import reactor.test.StepVerifier
import java.util.Arrays

@ExtendWith(MockitoExtension::class)
class DialoguePipelineServiceTest {
    @Mock
    private lateinit var inputService: DialogueInputService

    @Mock
    private lateinit var llmStreamService: DialogueLlmStreamService

    @Mock
    private lateinit var ttsStreamService: DialogueTtsStreamService

    @Mock
    private lateinit var postProcessingService: DialoguePostProcessingService

    private lateinit var service: DialoguePipelineService

    @BeforeEach
    fun setUp() {
        service =
            DialoguePipelineService(
                inputService,
                llmStreamService,
                ttsStreamService,
                postProcessingService,
            )
    }

    @Test
    @DisplayName("오디오 스트리밍 실행은 전체 파이프라인 단계를 위임 호출한다")
    fun executeAudioStreaming_shouldUseDelegatedFlows() {
        val session = ConversationSessionFixture.create()
        val testText = "test"
        val inputs =
            PipelineInputs(
                session,
                null,
                null,
                null,
                ConversationTurn.create(session.sessionId(), testText),
            )

        `when`(inputService.prepareInputs(eq(session), eq(testText))).thenReturn(Mono.just(inputs))
        `when`(ttsStreamService.prepareTtsWarmup()).thenReturn(Mono.empty())
        `when`(llmStreamService.buildLlmTokenStream(any())).thenReturn(Flux.just("a", "b"))
        `when`(ttsStreamService.assembleSentences(any())).thenReturn(Flux.just("ab"))
        `when`(ttsStreamService.buildAudioStream(any(), any(), any(), any()))
            .thenReturn(Flux.just("audio".toByteArray()))
        `when`(ttsStreamService.traceTtsSynthesis(any()))
            .thenReturn(Flux.just("audio".toByteArray()))
        `when`(postProcessingService.persistAndExtract(any(), any())).thenReturn(Mono.empty())

        StepVerifier
            .create(service.executeAudioStreaming(session, testText, AudioFormat.MP3))
            .expectNextMatches { bytes -> Arrays.equals(bytes, "audio".toByteArray()) }
            .verifyComplete()

        verify(inputService).prepareInputs(session, testText)
        verify(postProcessingService).persistAndExtract(any(), any())
    }

    @Test
    @DisplayName("텍스트 전용 실행은 LLM 토큰 스트림을 반환한다")
    fun executeTextOnly_shouldDelegateToUseCase() {
        val session = ConversationSessionFixture.create()
        val testText = "hello"
        val inputs =
            PipelineInputs(
                session,
                null,
                null,
                null,
                ConversationTurn.create(session.sessionId(), testText),
            )

        `when`(inputService.prepareInputs(eq(session), eq(testText))).thenReturn(Mono.just(inputs))
        `when`(llmStreamService.buildLlmTokenStream(any())).thenReturn(Flux.just("hi"))
        `when`(postProcessingService.persistAndExtractText(any(), any())).thenReturn(Mono.empty())

        StepVerifier
            .create(service.executeTextOnly(session, testText))
            .expectNext("hi")
            .verifyComplete()

        verify(inputService, times(1)).prepareInputs(session, testText)
    }
}
