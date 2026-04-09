package com.miyou.app.application.dialogue.pipeline

import com.miyou.app.application.dialogue.pipeline.stage.DialogueInputService
import com.miyou.app.application.dialogue.pipeline.stage.DialogueLlmStreamService
import com.miyou.app.application.dialogue.pipeline.stage.DialoguePostProcessingService
import com.miyou.app.application.dialogue.pipeline.stage.DialogueTtsStreamService
import com.miyou.app.domain.dialogue.model.ConversationContext
import com.miyou.app.domain.dialogue.model.ConversationTurn
import com.miyou.app.domain.memory.model.MemoryRetrievalResult
import com.miyou.app.domain.retrieval.model.RetrievalContext
import com.miyou.app.domain.voice.model.AudioFormat
import com.miyou.app.fixture.ConversationSessionFixture
import com.miyou.app.support.anyValue
import com.miyou.app.support.eqValue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
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
    @DisplayName("오디오 스트리밍 실행 시 전체 파이프라인에 처리를 위임한다")
    fun executeAudioStreaming_shouldUseDelegatedFlows() {
        val session = ConversationSessionFixture.create()
        val text = "test"
        val currentTurn = ConversationTurn.create(session.sessionId, text)
        val inputs =
            PipelineInputs(
                session,
                RetrievalContext.empty(text),
                MemoryRetrievalResult.empty(),
                ConversationContext.empty(),
                currentTurn,
            )

        `when`(inputService.prepareInputs(eqValue(session), eqValue(text))).thenReturn(Mono.just(inputs))
        `when`(ttsStreamService.prepareTtsWarmup()).thenReturn(Mono.empty())
        `when`(llmStreamService.buildLlmTokenStream(anyValue())).thenReturn(Flux.just("a", "b"))
        `when`(ttsStreamService.assembleSentences(anyValue())).thenReturn(Flux.just("ab"))
        `when`(ttsStreamService.buildAudioStream(anyValue(), anyValue(), anyValue(), anyValue()))
            .thenReturn(Flux.just("audio".toByteArray()))
        `when`(ttsStreamService.traceTtsSynthesis(anyValue()))
            .thenReturn(Flux.just("audio".toByteArray()))
        `when`(postProcessingService.persistAndExtract(anyValue(), anyValue())).thenReturn(Mono.empty())

        StepVerifier
            .create(service.executeAudioStreaming(session, text, AudioFormat.MP3))
            .expectNextMatches { bytes -> Arrays.equals(bytes, "audio".toByteArray()) }
            .verifyComplete()

        verify(inputService).prepareInputs(session, text)
        verify(postProcessingService).persistAndExtract(anyValue(), anyValue())
    }

    @Test
    @DisplayName("텍스트 전용 실행 시 위임된 토큰 스트림을 반환한다")
    fun executeTextOnly_shouldDelegateToUseCase() {
        val session = ConversationSessionFixture.create()
        val text = "hello"
        val currentTurn = ConversationTurn.create(session.sessionId, text)
        val inputs =
            PipelineInputs(
                session,
                RetrievalContext.empty(text),
                MemoryRetrievalResult.empty(),
                ConversationContext.empty(),
                currentTurn,
            )

        `when`(inputService.prepareInputs(eqValue(session), eqValue(text))).thenReturn(Mono.just(inputs))
        `when`(llmStreamService.buildLlmTokenStream(anyValue())).thenReturn(Flux.just("hi"))
        `when`(postProcessingService.persistAndExtractText(anyValue(), anyValue())).thenReturn(Mono.empty())

        StepVerifier
            .create(service.executeTextOnly(session, text))
            .expectNext("hi")
            .verifyComplete()

        verify(inputService, times(1)).prepareInputs(session, text)
    }
}
