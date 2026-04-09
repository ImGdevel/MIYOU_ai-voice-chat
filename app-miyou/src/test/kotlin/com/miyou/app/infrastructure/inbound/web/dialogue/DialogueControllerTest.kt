package com.miyou.app.infrastructure.inbound.web.dialogue

import com.miyou.app.application.credit.usecase.CreditChargeUseCase
import com.miyou.app.application.credit.usecase.CreditQueryUseCase
import com.miyou.app.application.dialogue.service.DialogueSpeechService
import com.miyou.app.domain.dialogue.port.ConversationSessionRepository
import com.miyou.app.domain.dialogue.port.DialoguePipelineUseCase
import com.miyou.app.domain.voice.model.AudioFormat
import com.miyou.app.fixture.ConversationSessionFixture
import com.miyou.app.fixture.UserCreditFixture
import com.miyou.app.infrastructure.inbound.web.dialogue.dto.RagDialogueRequest
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.mockito.ArgumentMatchers.eq
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
class DialogueControllerTest {
    @Autowired
    private lateinit var webTestClient: WebTestClient

    @MockitoBean
    private lateinit var dialoguePipelineUseCase: DialoguePipelineUseCase

    @MockitoBean
    private lateinit var dialogueSpeechService: DialogueSpeechService

    @MockitoBean
    private lateinit var sessionRepository: ConversationSessionRepository

    @MockitoBean
    private lateinit var creditQueryUseCase: CreditQueryUseCase

    @MockitoBean
    private lateinit var creditChargeUseCase: CreditChargeUseCase

    @Test
    @DisplayName("텍스트 질의 요청 시 스트림 응답을 반환한다")
    fun ragDialogueText_shouldReturnStream() {
        val sessionIdValue = "test-session-1"
        val session = ConversationSessionFixture.create(sessionIdValue)
        val request = RagDialogueRequest(sessionIdValue, "Hello world", Instant.now())

        `when`(sessionRepository.findById(eq(session.sessionId()))).thenReturn(Mono.just(session))
        `when`(dialoguePipelineUseCase.executeTextOnly(eq(session), eq("Hello world")))
            .thenReturn(Flux.just("token1", "token2"))

        webTestClient
            .post()
            .uri("/rag/dialogue/text")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(request)
            .exchange()
            .expectStatus()
            .isOk

        verify(dialoguePipelineUseCase).executeTextOnly(session, "Hello world")
    }

    @Test
    @DisplayName("오디오 요청 시 기본값으로 WAV를 사용한다")
    fun ragDialogueAudio_shouldDelegateToWav() {
        val sessionIdValue = "test-session-2"
        val session = ConversationSessionFixture.create(sessionIdValue)
        val request = RagDialogueRequest(sessionIdValue, "Default audio", Instant.now())

        `when`(sessionRepository.findById(eq(session.sessionId()))).thenReturn(Mono.just(session))
        `when`(creditQueryUseCase.getBalance(session.userId()))
            .thenReturn(Mono.just(UserCreditFixture.create(session.userId(), 5000L)))
        `when`(dialoguePipelineUseCase.executeAudioStreaming(eq(session), eq("Default audio"), eq(AudioFormat.WAV)))
            .thenReturn(Flux.just("audio".toByteArray()))

        webTestClient
            .post()
            .uri("/rag/dialogue/audio")
            .contentType(MediaType.APPLICATION_JSON)
            .accept(MediaType.parseMediaType("audio/wav"))
            .bodyValue(request)
            .exchange()
            .expectStatus()
            .isOk
            .expectHeader()
            .contentType("audio/wav")
    }

    @Test
    @DisplayName("빈 텍스트면 400을 반환한다")
    fun ragDialogueText_withBlankText_shouldReturnBadRequest() {
        val request = RagDialogueRequest(ConversationSessionFixture.DEFAULT_SESSION_ID, "", Instant.now())

        webTestClient
            .post()
            .uri("/rag/dialogue/text")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(request)
            .exchange()
            .expectStatus()
            .isBadRequest
    }
}
