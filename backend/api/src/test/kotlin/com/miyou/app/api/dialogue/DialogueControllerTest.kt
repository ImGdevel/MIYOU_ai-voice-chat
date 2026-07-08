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
    @DisplayName("ragDialogueText는 SSE 토큰 스트림을 반환한다")
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
    @DisplayName("createSession은 userId가 비어있을 때 400을 반환한다")
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
    @DisplayName("ragDialogueAudio는 기본 WAV 포맷을 사용한다")
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
    @DisplayName("ragDialogueText는 텍스트가 비어있을 때 400을 반환한다")
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
    @DisplayName("ragDialogueText는 sessionId가 128자를 초과할 때 400을 반환한다 (500이 아님)")
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
    @DisplayName("createSession은 personaId가 64자를 초과할 때 400을 반환한다 (500이 아님)")
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
    @DisplayName("createSession은 personaId에 유효하지 않은 문자가 포함되어 있을 때 400을 반환한다 (500이 아님)")
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
    @DisplayName("createSession은 명시적인 JSON null personaId를 허용한다 (기본 페르소나로 대체됨)")
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
