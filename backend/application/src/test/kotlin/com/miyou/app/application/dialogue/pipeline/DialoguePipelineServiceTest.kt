package com.miyou.app.application.dialogue.pipeline

import com.miyou.app.application.dialogue.pipeline.stage.DialogueInputService
import com.miyou.app.application.dialogue.pipeline.stage.DialogueLlmStreamService
import com.miyou.app.application.dialogue.pipeline.stage.DialoguePostProcessingService
import com.miyou.app.application.dialogue.pipeline.stage.DialogueTtsStreamService
import com.miyou.app.common.model.AudioFormat
import com.miyou.app.domain.dialogue.model.ConversationContext
import com.miyou.app.domain.dialogue.model.ConversationTurn
import com.miyou.app.domain.dialogue.port.CreditDeductCommand
import com.miyou.app.domain.dialogue.port.CreditDeductResult
import com.miyou.app.domain.dialogue.port.CreditRefundCommand
import com.miyou.app.domain.dialogue.port.CreditRefundResult
import com.miyou.app.domain.dialogue.port.DialogueCreditChargingPort
import com.miyou.app.domain.memory.model.MemoryRetrievalResult
import com.miyou.app.domain.retrieval.model.RetrievalContext
import com.miyou.app.fixture.ConversationSessionFixture
import com.miyou.app.support.anyValue
import com.miyou.app.support.eqValue
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mock
import org.mockito.Mockito.never
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
    @Mock private lateinit var inputService: DialogueInputService

    @Mock private lateinit var llmStreamService: DialogueLlmStreamService

    @Mock private lateinit var ttsStreamService: DialogueTtsStreamService

    @Mock private lateinit var postProcessingService: DialoguePostProcessingService

    @Mock private lateinit var creditChargingPort: DialogueCreditChargingPort

    private lateinit var service: DialoguePipelineService

    @BeforeEach
    fun setUp() {
        service =
            DialoguePipelineService(
                inputService,
                llmStreamService,
                ttsStreamService,
                postProcessingService,
                creditChargingPort,
            )
    }

    // ──────────────────────────────────────────────
    //  오디오 스트리밍 실행
    // ──────────────────────────────────────────────

    @Nested
    @DisplayName("오디오 스트리밍 실행")
    inner class ExecuteAudioStreaming {
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
            `when`(creditChargingPort.deduct(CreditDeductCommand(session.userId, session.sessionId.value)))
                .thenReturn(Mono.just(CreditDeductResult("tx-deduct")))

            StepVerifier
                .create(service.executeAudioStreaming(session, text, AudioFormat.MP3))
                .expectNextMatches { bytes -> Arrays.equals(bytes, "audio".toByteArray()) }
                .verifyComplete()

            verify(inputService).prepareInputs(session, text)
            verify(postProcessingService).persistAndExtract(anyValue(), anyValue())
        }

        @Test
        @DisplayName("LLM/TTS 실패 시 선차감한 크레딧을 환불한다")
        fun executeAudioStreaming_refundsOnServiceFailure() {
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
            val failure = RuntimeException("tts failed")

            `when`(inputService.prepareInputs(eqValue(session), eqValue(text))).thenReturn(Mono.just(inputs))
            `when`(ttsStreamService.prepareTtsWarmup()).thenReturn(Mono.empty())
            `when`(llmStreamService.buildLlmTokenStream(anyValue())).thenReturn(Flux.just("a"))
            `when`(ttsStreamService.assembleSentences(anyValue())).thenReturn(Flux.just("a"))
            `when`(ttsStreamService.buildAudioStream(anyValue(), anyValue(), anyValue(), anyValue()))
                .thenReturn(Flux.error(failure))
            `when`(ttsStreamService.traceTtsSynthesis(anyValue())).thenReturn(Flux.error(failure))
            `when`(postProcessingService.persistAndExtract(anyValue(), anyValue())).thenReturn(Mono.empty())
            `when`(creditChargingPort.deduct(CreditDeductCommand(session.userId, session.sessionId.value)))
                .thenReturn(Mono.just(CreditDeductResult("tx-deduct")))
            `when`(creditChargingPort.refund(CreditRefundCommand(session.userId, session.sessionId.value)))
                .thenReturn(Mono.just(CreditRefundResult("tx-refund")))

            StepVerifier
                .create(service.executeAudioStreaming(session, text, AudioFormat.MP3))
                .expectErrorSatisfies { error -> assertThat(error).isSameAs(failure) }
                .verify()

            verify(creditChargingPort).deduct(CreditDeductCommand(session.userId, session.sessionId.value))
            verify(creditChargingPort).refund(CreditRefundCommand(session.userId, session.sessionId.value))
        }

        @Test
        @DisplayName("대화 저장(postProcessing) 실패 시 크레딧을 환불하지 않는다")
        fun executeAudioStreaming_doesNotRefundWhenPostProcessingFails() {
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
            `when`(llmStreamService.buildLlmTokenStream(anyValue())).thenReturn(Flux.just("a"))
            `when`(ttsStreamService.assembleSentences(anyValue())).thenReturn(Flux.just("a"))
            `when`(ttsStreamService.buildAudioStream(anyValue(), anyValue(), anyValue(), anyValue()))
                .thenReturn(Flux.just("audio".toByteArray()))
            `when`(ttsStreamService.traceTtsSynthesis(anyValue()))
                .thenReturn(Flux.just("audio".toByteArray()))
            // postProcessing 실패
            `when`(postProcessingService.persistAndExtract(anyValue(), anyValue()))
                .thenReturn(Mono.error(RuntimeException("mongodb unavailable")))
            `when`(creditChargingPort.deduct(CreditDeductCommand(session.userId, session.sessionId.value)))
                .thenReturn(Mono.just(CreditDeductResult("tx-deduct")))

            StepVerifier
                .create(service.executeAudioStreaming(session, text, AudioFormat.MP3))
                .expectNextMatches { bytes -> Arrays.equals(bytes, "audio".toByteArray()) }
                .expectError(RuntimeException::class.java)
                .verify()

            // postProcessing 실패는 크레딧 환불을 유발하지 않는다
            verify(creditChargingPort, never()).refund(anyValue())
        }
    }

    // ──────────────────────────────────────────────
    //  텍스트 전용 실행
    // ──────────────────────────────────────────────

    @Nested
    @DisplayName("텍스트 전용 실행")
    inner class ExecuteTextOnly {
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
            `when`(creditChargingPort.deduct(CreditDeductCommand(session.userId, session.sessionId.value)))
                .thenReturn(Mono.just(CreditDeductResult("tx-deduct")))

            StepVerifier
                .create(service.executeTextOnly(session, text))
                .expectNext("hi")
                .verifyComplete()

            verify(inputService, times(1)).prepareInputs(session, text)
        }

        @Test
        @DisplayName("LLM 스트림 실패 시 선차감한 크레딧을 환불한다")
        fun executeTextOnly_refundsPrechargedCreditOnServiceFailure() {
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
            val failure = IllegalStateException("llm failed")

            `when`(inputService.prepareInputs(eqValue(session), eqValue(text))).thenReturn(Mono.just(inputs))
            `when`(llmStreamService.buildLlmTokenStream(anyValue())).thenReturn(Flux.error(failure))
            `when`(postProcessingService.persistAndExtractText(anyValue(), anyValue())).thenReturn(Mono.empty())
            `when`(creditChargingPort.deduct(CreditDeductCommand(session.userId, session.sessionId.value)))
                .thenReturn(Mono.just(CreditDeductResult("tx-deduct")))
            `when`(creditChargingPort.refund(CreditRefundCommand(session.userId, session.sessionId.value)))
                .thenReturn(Mono.just(CreditRefundResult("tx-refund")))

            StepVerifier
                .create(service.executeTextOnly(session, text))
                .expectErrorSatisfies { error -> assertThat(error).isSameAs(failure) }
                .verify()

            verify(creditChargingPort).deduct(CreditDeductCommand(session.userId, session.sessionId.value))
            verify(creditChargingPort).refund(CreditRefundCommand(session.userId, session.sessionId.value))
        }

        @Test
        @DisplayName("정상 완료 시 선차감한 크레딧을 환불하지 않는다")
        fun executeTextOnly_keepsPrechargedCreditOnSuccess() {
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
            `when`(creditChargingPort.deduct(CreditDeductCommand(session.userId, session.sessionId.value)))
                .thenReturn(Mono.just(CreditDeductResult("tx-deduct")))

            StepVerifier
                .create(service.executeTextOnly(session, text))
                .expectNext("hi")
                .verifyComplete()

            verify(creditChargingPort, never()).refund(anyValue())
        }

        @Test
        @DisplayName("대화 저장(postProcessing) 실패 시 크레딧을 환불하지 않는다")
        fun executeTextOnly_doesNotRefundWhenPostProcessingFails() {
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
            // postProcessing 실패
            `when`(postProcessingService.persistAndExtractText(anyValue(), anyValue()))
                .thenReturn(Mono.error(RuntimeException("mongodb unavailable")))
            `when`(creditChargingPort.deduct(CreditDeductCommand(session.userId, session.sessionId.value)))
                .thenReturn(Mono.just(CreditDeductResult("tx-deduct")))

            StepVerifier
                .create(service.executeTextOnly(session, text))
                .expectNext("hi")
                .expectError(RuntimeException::class.java)
                .verify()

            // postProcessing 실패는 크레딧 환불을 유발하지 않는다
            verify(creditChargingPort, never()).refund(anyValue())
        }

        @Test
        @DisplayName("사용자가 스트림을 취소해도 크레딧을 환불하지 않는다")
        fun executeTextOnly_doesNotRefundOnUserCancellation() {
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
            // 무한 스트림: 사용자가 구독을 취소하기 전까지 토큰을 계속 발행
            `when`(llmStreamService.buildLlmTokenStream(anyValue()))
                .thenReturn(Flux.just("tok1", "tok2", "tok3").concatWith(Flux.never()))
            `when`(postProcessingService.persistAndExtractText(anyValue(), anyValue())).thenReturn(Mono.empty())
            `when`(creditChargingPort.deduct(CreditDeductCommand(session.userId, session.sessionId.value)))
                .thenReturn(Mono.just(CreditDeductResult("tx-deduct")))

            StepVerifier
                .create(service.executeTextOnly(session, text))
                .expectNext("tok1")
                .thenCancel() // 구독자가 직접 취소 (클라이언트 연결 끊김 시뮬레이션)
                .verify()

            verify(creditChargingPort).deduct(CreditDeductCommand(session.userId, session.sessionId.value))
            // cancel 핸들러는 환불하지 않는다
            verify(creditChargingPort, never()).refund(anyValue())
        }
    }
}
