package com.miyou.app.infrastructure.inbound.web.dialogue

import com.miyou.app.application.credit.usecase.CreditChargeUseCase
import com.miyou.app.application.credit.usecase.CreditQueryUseCase
import com.miyou.app.application.dialogue.service.DialogueSpeechService
import com.miyou.app.domain.dialogue.model.ConversationSessionId
import com.miyou.app.domain.dialogue.port.ConversationSessionRepository
import com.miyou.app.domain.dialogue.port.DialoguePipelineUseCase
import com.miyou.app.domain.voice.model.AudioFormat
import com.miyou.app.fixture.ConversationSessionFixture
import com.miyou.app.fixture.UserCreditFixture
import com.miyou.app.infrastructure.inbound.web.dialogue.dto.RagDialogueRequest
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.mockito.ArgumentMatchers.any
import org.mockito.ArgumentMatchers.eq
import org.mockito.Mockito.never
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.reactive.WebFluxTest
import org.springframework.http.MediaType
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.web.reactive.server.WebTestClient
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import java.time.Instant

@WebFluxTest(DialogueController::class)
@DisplayName("DialogueController 크레딧 연동 테스트")
class DialogueControllerCreditTest {
    @Autowired
    private lateinit var webTestClient: WebTestClient

    @MockitoBean
    private lateinit var dialoguePipelineUseCase: DialoguePipelineUseCase

    @MockitoBean
    private lateinit var sessionRepository: ConversationSessionRepository

    @MockitoBean
    private lateinit var dialogueSpeechService: DialogueSpeechService

    @MockitoBean
    private lateinit var creditQueryUseCase: CreditQueryUseCase

    @MockitoBean
    private lateinit var creditChargeUseCase: CreditChargeUseCase

    @Nested
    @DisplayName("POST /rag/dialogue/audio 의 크레딧 체크")
    inner class AudioCreditCheck {
        @Test
        @DisplayName("잔액이 충분하면 파이프라인을 실행하고 200을 반환한다")
        fun audio_sufficientCredit_executionPipeline() {
            val sessionIdValue = "credit-session-1"
            val session = ConversationSessionFixture.create(sessionIdValue)
            val request = RagDialogueRequest(sessionIdValue, "안녕", Instant.now())

            `when`(sessionRepository.findById(ConversationSessionId.of(sessionIdValue))).thenReturn(Mono.just(session))
            `when`(creditQueryUseCase.getBalance(eq(session.userId())))
                .thenReturn(Mono.just(UserCreditFixture.create(session.userId(), 4900L)))
            `when`(dialoguePipelineUseCase.executeAudioStreaming(eq(session), eq("안녕"), eq(AudioFormat.WAV)))
                .thenReturn(Flux.just("audio-bytes".toByteArray()))

            webTestClient
                .post()
                .uri("/rag/dialogue/audio")
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.parseMediaType("audio/wav"))
                .bodyValue(request)
                .exchange()
                .expectStatus()
                .isOk
        }

        @Test
        @DisplayName("잔액이 부족하면 402를 반환하고 파이프라인을 실행하지 않는다")
        fun audio_insufficientCredit_returns402() {
            val sessionIdValue = "credit-session-low"
            val session = ConversationSessionFixture.create(sessionIdValue)
            val request = RagDialogueRequest(sessionIdValue, "안녕", Instant.now())

            `when`(sessionRepository.findById(ConversationSessionId.of(sessionIdValue))).thenReturn(Mono.just(session))
            `when`(creditQueryUseCase.getBalance(eq(session.userId())))
                .thenReturn(Mono.just(UserCreditFixture.create(session.userId(), 99L)))

            webTestClient
                .post()
                .uri("/rag/dialogue/audio")
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.parseMediaType("audio/wav"))
                .bodyValue(request)
                .exchange()
                .expectStatus()
                .isEqualTo(402)

            verify(dialoguePipelineUseCase, never()).executeAudioStreaming(any(), any(), any())
        }
    }
}
