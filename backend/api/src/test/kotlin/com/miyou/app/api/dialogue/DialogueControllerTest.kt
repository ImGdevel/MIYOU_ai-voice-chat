package com.miyou.app.api.dialogue

import com.miyou.app.api.dialogue.dto.CreateSessionRequest
import com.miyou.app.api.dialogue.dto.RagDialogueRequest
import com.miyou.app.application.credit.usecase.CreditChargeUseCase
import com.miyou.app.application.dialogue.service.DialogueSpeechService
import com.miyou.app.common.model.AudioFormat
import com.miyou.app.domain.dialogue.model.ConversationSessionId
import com.miyou.app.domain.dialogue.port.ConversationSessionRepository
import com.miyou.app.domain.dialogue.port.DialoguePipelineUseCase
import com.miyou.app.fixture.ConversationSessionFixture
import com.miyou.app.support.PermitAllSecurityTestConfig
import com.miyou.app.support.anyValue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.mockito.Mockito.`when`
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.reactive.WebFluxTest
import org.springframework.context.annotation.Import
import org.springframework.http.MediaType
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.web.reactive.server.WebTestClient
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import java.time.Instant

@Import(PermitAllSecurityTestConfig::class)
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
    @DisplayName("ragDialogueAudio uses the default WAV format")
    fun ragDialogueAudio_usesDefaultWavFormat() {
        val sessionIdValue = "test-session-2"
        val session = ConversationSessionFixture.create(sessionIdValue)
        val request = RagDialogueRequest(sessionIdValue, "Default audio", Instant.now())

        `when`(sessionRepository.findById(ConversationSessionId.of(sessionIdValue))).thenReturn(Mono.just(session))
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

    @Test
    @DisplayName("ragDialogueText returns 400 when sessionId exceeds 128 characters (not 500)")
    fun ragDialogueText_returns400ForTooLongSessionId() {
        val tooLongSessionId = "s".repeat(129)
        val request = RagDialogueRequest(tooLongSessionId, "Hello world", Instant.now())

        webTestClient
            .post()
            .uri("/rag/dialogue/text")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(request)
            .exchange()
            .expectStatus()
            .isBadRequest
    }

    @Test
    @DisplayName("createSession returns 400 when personaId exceeds 64 characters (not 500)")
    fun createSession_returns400ForTooLongPersonaId() {
        val request = CreateSessionRequest(userId = "user-1", personaId = "p".repeat(65))

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
    @DisplayName("createSession returns 400 when personaId contains invalid characters (not 500)")
    fun createSession_returns400ForInvalidPersonaIdCharacters() {
        val request = CreateSessionRequest(userId = "user-1", personaId = "invalid persona!")

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
    @DisplayName("createSession accepts an explicit JSON null personaId (falls back to default persona)")
    fun createSession_acceptsExplicitNullPersonaId() {
        val session = ConversationSessionFixture.create("session-null-persona")

        `when`(sessionRepository.save(anyValue())).thenReturn(Mono.just(session))
        `when`(creditChargeUseCase.initializeIfAbsent(anyValue())).thenReturn(Mono.empty())

        webTestClient
            .post()
            .uri("/rag/dialogue/session")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue("""{"userId":"user-1","personaId":null}""")
            .exchange()
            .expectStatus()
            .isOk
    }
}
