package com.miyou.app.infrastructure.inbound.web.dialogue

import com.miyou.app.application.credit.usecase.CreditChargeUseCase
import com.miyou.app.application.credit.usecase.CreditQueryUseCase
import com.miyou.app.application.dialogue.service.DialogueSpeechService
import com.miyou.app.domain.dialogue.model.ConversationSession
import com.miyou.app.domain.dialogue.model.ConversationSessionId
import com.miyou.app.domain.dialogue.port.ConversationSessionRepository
import com.miyou.app.domain.dialogue.port.DialoguePipelineUseCase
import com.miyou.app.domain.voice.model.AudioFormat
import com.miyou.app.fixture.ConversationSessionFixture
import com.miyou.app.fixture.UserCreditFixture
import com.miyou.app.infrastructure.inbound.web.dialogue.dto.CreateSessionRequest
import com.miyou.app.infrastructure.inbound.web.dialogue.dto.RagDialogueRequest
import com.miyou.app.support.anyValue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
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
    @DisplayName("ragDialogueText returns an SSE token stream")
    fun ragDialogueText_returnsTokenStream() {
        val sessionIdValue = "test-session-1"
        val session = ConversationSessionFixture.create(sessionIdValue)
        val request = RagDialogueRequest(sessionIdValue, "Hello world", Instant.now())

        `when`(sessionRepository.findById(ConversationSessionId.of(sessionIdValue))).thenReturn(Mono.just(session))
        `when`(dialoguePipelineUseCase.executeTextOnly(session, "Hello world"))
            .thenReturn(Flux.just("token1", "token2"))

        webTestClient
            .post()
            .uri("/rag/dialogue/text")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(request)
            .exchange()
            .expectStatus()
            .isOk
    }

    @Test
    @DisplayName("createSession returns 400 when userId is blank")
    fun createSession_returns400ForBlankUserId() {
        val request = CreateSessionRequest(userId = "", personaId = "default")

        webTestClient
            .post()
            .uri("/rag/dialogue/session")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(request)
            .exchange()
            .expectStatus()
            .isBadRequest
    }

    @Test
    @DisplayName("createSession accepts a 128 character userId")
    fun createSession_acceptsMaxLengthUserId() {
        val userId = "u".repeat(128)
        val request = CreateSessionRequest(userId = userId, personaId = "default")

        `when`(sessionRepository.save(anyValue())).thenAnswer { invocation ->
            Mono.just(invocation.arguments[0] as ConversationSession)
        }
        `when`(creditChargeUseCase.initializeIfAbsent(anyValue())).thenReturn(Mono.empty())

        webTestClient
            .post()
            .uri("/rag/dialogue/session")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(request)
            .exchange()
            .expectStatus()
            .isOk
            .expectBody()
            .jsonPath("$.userId")
            .isEqualTo(userId)
    }

    @Test
    @DisplayName("createSession returns 400 when userId exceeds 128 characters")
    fun createSession_returns400ForTooLongUserId() {
        val request = CreateSessionRequest(userId = "u".repeat(129), personaId = "default")

        webTestClient
            .post()
            .uri("/rag/dialogue/session")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(request)
            .exchange()
            .expectStatus()
            .isBadRequest
    }

    @Test
    @DisplayName("ragDialogueAudio uses the default WAV format")
    fun ragDialogueAudio_usesDefaultWavFormat() {
        val sessionIdValue = "test-session-2"
        val session = ConversationSessionFixture.create(sessionIdValue)
        val request = RagDialogueRequest(sessionIdValue, "Default audio", Instant.now())

        `when`(sessionRepository.findById(ConversationSessionId.of(sessionIdValue))).thenReturn(Mono.just(session))
        `when`(creditQueryUseCase.getBalance(session.userId))
            .thenReturn(Mono.just(UserCreditFixture.create(session.userId, 5000L)))
        `when`(dialoguePipelineUseCase.executeAudioStreaming(session, "Default audio", AudioFormat.WAV))
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
    @DisplayName("ragDialogueText returns 400 for blank text")
    fun ragDialogueText_returns400ForBlankText() {
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
