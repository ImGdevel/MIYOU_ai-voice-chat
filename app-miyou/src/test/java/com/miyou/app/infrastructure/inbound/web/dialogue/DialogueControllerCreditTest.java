package com.miyou.app.infrastructure.inbound.web.dialogue;

import java.time.Instant;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.WebFluxTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.reactive.server.WebTestClient;

import com.miyou.app.application.credit.usecase.CreditChargeUseCase;
import com.miyou.app.application.credit.usecase.CreditQueryUseCase;
import com.miyou.app.application.dialogue.service.DialogueSpeechService;
import com.miyou.app.domain.dialogue.model.ConversationSession;
import com.miyou.app.domain.dialogue.model.ConversationSessionId;
import com.miyou.app.domain.dialogue.port.ConversationSessionRepository;
import com.miyou.app.domain.dialogue.port.DialoguePipelineUseCase;
import com.miyou.app.domain.voice.model.AudioFormat;
import com.miyou.app.fixture.ConversationSessionFixture;
import com.miyou.app.fixture.UserCreditFixture;
import com.miyou.app.infrastructure.inbound.web.dialogue.dto.RagDialogueRequest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * DialogueController의 크레딧 관련 동작 검증. - Pre-flight 잔액 확인 (402 반환) - 충분한 잔액 시 파이프라인 정상 실행
 */
@WebFluxTest(DialogueController.class)
@DisplayName("DialogueController 크레딧 연동 테스트")
class DialogueControllerCreditTest {

	@Autowired
	private WebTestClient webTestClient;

	@MockitoBean
	private DialoguePipelineUseCase dialoguePipelineUseCase;

	@MockitoBean
	private ConversationSessionRepository sessionRepository;

	@MockitoBean
	private DialogueSpeechService dialogueSpeechService;

	@MockitoBean
	private CreditQueryUseCase creditQueryUseCase;

	@MockitoBean
	private CreditChargeUseCase creditChargeUseCase;

	// ── 오디오 엔드포인트 크레딧 확인 ─────────────────────────────────────

	@Nested
	@DisplayName("POST /rag/dialogue/audio — 크레딧 체크")
	class AudioCreditCheck {

		@Test
		@DisplayName("잔액이 100 이상이면 파이프라인을 실행하고 200을 반환한다")
		void audio_sufficientCredit_executionPipeline() {
			String sessionIdValue = "credit-session-1";
			ConversationSession session = ConversationSessionFixture.create(sessionIdValue);
			RagDialogueRequest request = new RagDialogueRequest(sessionIdValue, "안녕",
				Instant.now());

			when(sessionRepository.findById(ConversationSessionId.of(sessionIdValue)))
				.thenReturn(Mono.just(session));
			when(creditQueryUseCase.getBalance(eq(session.userId())))
				.thenReturn(Mono.just(UserCreditFixture.create(session.userId(), 4900L)));
			when(dialoguePipelineUseCase
				.executeAudioStreaming(eq(session), eq("안녕"), eq(AudioFormat.WAV)))
				.thenReturn(Flux.just("audio-bytes".getBytes()));

			webTestClient.post()
				.uri("/rag/dialogue/audio")
				.contentType(MediaType.APPLICATION_JSON)
				.accept(MediaType.parseMediaType("audio/wav"))
				.bodyValue(request)
				.exchange()
				.expectStatus().isOk();

			verify(dialoguePipelineUseCase)
				.executeAudioStreaming(eq(session), eq("안녕"), eq(AudioFormat.WAV));
		}

		@Test
		@DisplayName("잔액이 정확히 100이면 파이프라인을 실행한다 (경계값)")
		void audio_balanceExactlyAtThreshold_executes() {
			String sessionIdValue = "credit-session-boundary";
			ConversationSession session = ConversationSessionFixture.create(sessionIdValue);
			RagDialogueRequest request = new RagDialogueRequest(sessionIdValue, "테스트",
				Instant.now());

			when(sessionRepository.findById(ConversationSessionId.of(sessionIdValue)))
				.thenReturn(Mono.just(session));
			when(creditQueryUseCase.getBalance(eq(session.userId())))
				.thenReturn(Mono.just(UserCreditFixture.create(session.userId(), 100L)));
			when(dialoguePipelineUseCase.executeAudioStreaming(any(), any(), any()))
				.thenReturn(Flux.just("audio".getBytes()));

			webTestClient.post()
				.uri("/rag/dialogue/audio")
				.contentType(MediaType.APPLICATION_JSON)
				.accept(MediaType.parseMediaType("audio/wav"))
				.bodyValue(request)
				.exchange()
				.expectStatus().isOk();
		}

		@Test
		@DisplayName("잔액이 99로 부족하면 402 Payment Required를 반환하고 파이프라인을 실행하지 않는다")
		void audio_insufficientCredit_returns402() {
			String sessionIdValue = "credit-session-low";
			ConversationSession session = ConversationSessionFixture.create(sessionIdValue);
			RagDialogueRequest request = new RagDialogueRequest(sessionIdValue, "안녕",
				Instant.now());

			when(sessionRepository.findById(ConversationSessionId.of(sessionIdValue)))
				.thenReturn(Mono.just(session));
			when(creditQueryUseCase.getBalance(eq(session.userId())))
				.thenReturn(Mono.just(UserCreditFixture.create(session.userId(), 99L)));

			webTestClient.post()
				.uri("/rag/dialogue/audio")
				.contentType(MediaType.APPLICATION_JSON)
				.accept(MediaType.parseMediaType("audio/wav"))
				.bodyValue(request)
				.exchange()
				.expectStatus().isEqualTo(402);

			verify(dialoguePipelineUseCase, never()).executeAudioStreaming(any(), any(), any());
		}

		@Test
		@DisplayName("잔액이 0이면 402를 반환한다")
		void audio_zeroBalance_returns402() {
			String sessionIdValue = "credit-session-zero";
			ConversationSession session = ConversationSessionFixture.create(sessionIdValue);
			RagDialogueRequest request = new RagDialogueRequest(sessionIdValue, "안녕",
				Instant.now());

			when(sessionRepository.findById(ConversationSessionId.of(sessionIdValue)))
				.thenReturn(Mono.just(session));
			when(creditQueryUseCase.getBalance(eq(session.userId())))
				.thenReturn(Mono.just(UserCreditFixture.withZeroBalance(session.userId())));

			webTestClient.post()
				.uri("/rag/dialogue/audio")
				.contentType(MediaType.APPLICATION_JSON)
				.bodyValue(request)
				.exchange()
				.expectStatus().isEqualTo(402);

			verify(dialoguePipelineUseCase, never()).executeAudioStreaming(any(), any(), any());
		}

		@Test
		@DisplayName("세션이 존재하지 않으면 404를 반환한다 (크레딧 확인 이전)")
		void audio_sessionNotFound_returns404() {
			String unknownSession = "no-such-session";
			RagDialogueRequest request = new RagDialogueRequest(unknownSession, "안녕",
				Instant.now());

			when(sessionRepository.findById(ConversationSessionId.of(unknownSession)))
				.thenReturn(Mono.empty());

			webTestClient.post()
				.uri("/rag/dialogue/audio")
				.contentType(MediaType.APPLICATION_JSON)
				.bodyValue(request)
				.exchange()
				.expectStatus().isNotFound();

			verify(creditQueryUseCase, never()).getBalance(any());
		}
	}
}
