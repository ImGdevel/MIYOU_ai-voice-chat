package com.miyou.app.api.dialogue

import com.miyou.app.api.dialogue.dto.RagDialogueRequest
import com.miyou.app.application.credit.usecase.CreditChargeUseCase
import com.miyou.app.application.dialogue.service.DialogueSpeechService
import com.miyou.app.common.model.AudioFormat
import com.miyou.app.domain.credit.exception.InsufficientCreditException
import com.miyou.app.domain.dialogue.model.ConversationSessionId
import com.miyou.app.domain.dialogue.port.ConversationSessionRepository
import com.miyou.app.domain.dialogue.port.DialoguePipelineUseCase
import com.miyou.app.fixture.ConversationSessionFixture
import com.miyou.app.support.PermitAllSecurityTestConfig
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.mockito.Mockito.verify
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
    @DisplayName("사용자의 크레딧이 충분할 때 오디오 대화가 정상적으로 진행된다")
    fun audioDialogue_proceedsWhenCreditIsSufficient() {
        // given: 세션 ID 및 테스트 리퀘스트 데이터 설정
        val sessionIdValue = "credit-session-1"
        val session = ConversationSessionFixture.create(sessionIdValue)
        val request = RagDialogueRequest(sessionIdValue, "hello", Instant.now())

        `when`(sessionRepository.findById(ConversationSessionId.of(sessionIdValue))).thenReturn(Mono.just(session))
        `when`(dialoguePipelineUseCase.executeAudioStreaming(session, "hello", AudioFormat.WAV))
            .thenReturn(Flux.just("audio-bytes".toByteArray()))

        // when & then: 오디오 스트리밍 대화 요청 시 HTTP 200 응답 확인
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
    @DisplayName("크레딧이 부족할 때 오디오 대화는 402 코드를 반환한다")
    fun audioDialogue_returns402WhenCreditIsInsufficient() {
        // given: 크레딧이 부족한 사용자의 세션 정보
        val sessionIdValue = "credit-session-low"
        val session = ConversationSessionFixture.create(sessionIdValue)
        val request = RagDialogueRequest(sessionIdValue, "hello", Instant.now())

        `when`(sessionRepository.findById(ConversationSessionId.of(sessionIdValue))).thenReturn(Mono.just(session))
        `when`(dialoguePipelineUseCase.executeAudioStreaming(session, "hello", AudioFormat.WAV))
            .thenReturn(Flux.error(InsufficientCreditException(session.userId, 99L, 100L)))

        // when & then: 크레딧 부족 예외 발생 시 HTTP 402(Payment Required) 상태코드가 반환되는지 확인
        webTestClient
            .post()
            .uri("/rag/dialogue/audio")
            .contentType(MediaType.APPLICATION_JSON)
            .accept(MediaType.parseMediaType("audio/wav"))
            .bodyValue(request)
            .exchange()
            .expectStatus()
            .isEqualTo(402)

        // 실제로 파이프라인 유스케이스가 실행되었는지 확인
        verify(dialoguePipelineUseCase).executeAudioStreaming(session, "hello", AudioFormat.WAV)
    }
}
