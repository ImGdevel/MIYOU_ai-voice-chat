package com.miyou.app.infrastructure.inbound.web.dialogue

import com.miyou.app.application.credit.usecase.CreditChargeUseCase
import com.miyou.app.application.dialogue.service.DialogueSpeechService
import com.miyou.app.domain.credit.exception.InsufficientCreditException
import com.miyou.app.domain.dialogue.model.ConversationSessionId
import com.miyou.app.domain.dialogue.port.ConversationSessionRepository
import com.miyou.app.domain.dialogue.port.DialoguePipelineUseCase
import com.miyou.app.domain.voice.model.AudioFormat
import com.miyou.app.fixture.ConversationSessionFixture
import com.miyou.app.infrastructure.inbound.web.dialogue.dto.RagDialogueRequest
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
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
    private lateinit var creditChargeUseCase: CreditChargeUseCase

    @Test
    @DisplayName("audio dialogue proceeds when the user has enough credit")
    fun audioDialogue_proceedsWhenCreditIsSufficient() {
        val sessionIdValue = "credit-session-1"
        val session = ConversationSessionFixture.create(sessionIdValue)
        val request = RagDialogueRequest(sessionIdValue, "hello", Instant.now())

        `when`(sessionRepository.findById(ConversationSessionId.of(sessionIdValue))).thenReturn(Mono.just(session))
        `when`(dialoguePipelineUseCase.executeAudioStreaming(session, "hello", AudioFormat.WAV))
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
    @DisplayName("audio dialogue returns 402 when credit is insufficient")
    fun audioDialogue_returns402WhenCreditIsInsufficient() {
        val sessionIdValue = "credit-session-low"
        val session = ConversationSessionFixture.create(sessionIdValue)
        val request = RagDialogueRequest(sessionIdValue, "hello", Instant.now())

        `when`(sessionRepository.findById(ConversationSessionId.of(sessionIdValue))).thenReturn(Mono.just(session))
        `when`(dialoguePipelineUseCase.executeAudioStreaming(session, "hello", AudioFormat.WAV))
            .thenReturn(Flux.error(InsufficientCreditException(session.userId, 99L, 100L)))

        webTestClient
            .post()
            .uri("/rag/dialogue/audio")
            .contentType(MediaType.APPLICATION_JSON)
            .accept(MediaType.parseMediaType("audio/wav"))
            .bodyValue(request)
            .exchange()
            .expectStatus()
            .isEqualTo(402)

        verify(dialoguePipelineUseCase).executeAudioStreaming(session, "hello", AudioFormat.WAV)
    }
}
