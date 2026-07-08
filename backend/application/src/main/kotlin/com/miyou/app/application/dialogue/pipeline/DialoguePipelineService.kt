package com.miyou.app.application.dialogue.pipeline

import com.miyou.app.application.dialogue.pipeline.stage.DialogueInputService
import com.miyou.app.application.dialogue.pipeline.stage.DialogueLlmStreamService
import com.miyou.app.application.dialogue.pipeline.stage.DialoguePostProcessingService
import com.miyou.app.application.dialogue.pipeline.stage.DialogueTtsStreamService
import com.miyou.app.common.model.AudioFormat
import com.miyou.app.domain.dialogue.model.ConversationSession
import com.miyou.app.domain.dialogue.port.CreditDeductCommand
import com.miyou.app.domain.dialogue.port.CreditDeductResult
import com.miyou.app.domain.dialogue.port.CreditRefundCommand
import com.miyou.app.domain.dialogue.port.DialogueCreditChargingPort
import com.miyou.app.domain.dialogue.port.DialoguePipelineUseCase
import com.miyou.app.monitoring.aop.MonitoredPipeline
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.stereotype.Service
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono

/**
 * 대화 처리 파이프라인 (음성/텍스트).
 *
 * 입력 준비 → LLM 스트리밍 → TTS/텍스트 변환 → 크레딧 차감 & 후처리 순서로 실행.
 * 크레딧 정책: 스트림 시작 시 사전차감, 오류 발생 시 환불, 사용자 취소는 차감 유지.
 */
@Service
class DialoguePipelineService(
    private val inputService: DialogueInputService,
    private val llmStreamService: DialogueLlmStreamService,
    private val ttsStreamService: DialogueTtsStreamService,
    private val postProcessingService: DialoguePostProcessingService,
    private val creditChargingPort: DialogueCreditChargingPort,
) : DialoguePipelineUseCase {
    private val logger = KotlinLogging.logger {}

    /**
     * 음성 스트리밍 실행.
     *
     * @param session 대화 세션
     * @param text 사용자 입력 텍스트
     * @param format 음성 포맷 (기본값: MP3)
     * @return 음성 데이터 스트림
     */
    @MonitoredPipeline(inputArgIndex = 1)
    override fun executeAudioStreaming(
        session: ConversationSession,
        text: String,
        format: AudioFormat,
    ): Flux<ByteArray> {
        val targetFormat = format

        val inputsMono = inputService.prepareInputs(session, text).cache()
        val ttsWarmup: Mono<Void> = ttsStreamService.prepareTtsWarmup()

        val llmTokens: Flux<String> = llmStreamService.buildLlmTokenStream(inputsMono)
        val sentences = ttsStreamService.assembleSentences(llmTokens).cache()
        val audioFlux = ttsStreamService.buildAudioStream(sentences, ttsWarmup, targetFormat, session.personaId)
        val postProcessing = postProcessingService.persistAndExtract(inputsMono, sentences)
        val audioStream = ttsStreamService.traceTtsSynthesis(audioFlux)

        // 대화 저장 실패가 환불을 유발하지 않도록 후처리(postProcessing)는 크레딧 영역 밖에서 실행.
        return prechargeConversation(session, audioStream)
            .concatWith(postProcessing.thenMany(Flux.empty()))
    }

    /**
     * 텍스트 전용 스트리밍 실행.
     *
     * @param session 대화 세션
     * @param text 사용자 입력 텍스트
     * @return LLM 응답 토큰 스트림
     */
    @MonitoredPipeline(inputArgIndex = 1)
    override fun executeTextOnly(
        session: ConversationSession,
        text: String,
    ): Flux<String> {
        val inputsMono = inputService.prepareInputs(session, text).cache()

        val llmTokens = llmStreamService.buildLlmTokenStream(inputsMono)
        val textStream = llmTokens.cache()
        val postProcessing = postProcessingService.persistAndExtractText(inputsMono, textStream)

        // 대화 저장 실패가 환불을 유발하지 않도록 후처리(postProcessing)는 크레딧 영역 밖에서 실행.
        return prechargeConversation(session, textStream)
            .concatWith(postProcessing.thenMany(Flux.empty()))
    }

    /**
     * 대화 스트림에 선차감 정책을 적용합니다.
     * 내부 오류 발생 시에는 환불하되, 사용자 취소나 후처리 실패는 환불되지 않습니다.
     */
    private fun <T> prechargeConversation(
        session: ConversationSession,
        responseStream: Flux<T>,
    ): Flux<T> =
        Flux.usingWhen<T, CreditDeductResult>(
            creditChargingPort.deduct(CreditDeductCommand(session.userId, session.sessionId.value)),
            { _: CreditDeductResult -> responseStream },
            { _: CreditDeductResult -> Mono.empty<Void>() },
            { _: CreditDeductResult, exception: Throwable -> refundConversation(session, exception) },
            { _: CreditDeductResult -> logUserCancellation(session) },
        )

    /**
     * 크레딧 환불 처리.
     * 서비스 내부 오류(LLM/TTS 실패 등)로 스트림이 중단된 경우에만 호출.
     *
     * @param session 대화 세션
     * @param cause 예외 원인
     * @return 환불 처리 결과
     */
    private fun refundConversation(
        session: ConversationSession,
        cause: Throwable,
    ): Mono<Void> =
        creditChargingPort
            .refund(CreditRefundCommand(session.userId, session.sessionId.value))
            .doOnNext { result ->
                logger.warn {
                    "Conversation credit refunded - " +
                        "userId=${session.userId}, sessionId=${session.sessionId.value}, " +
                        "transactionId=${result.transactionId}, cause=${cause.message}"
                }
            }.then()
            .onErrorResume { refundError ->
                logger.error(refundError) {
                    "Conversation credit refund failed - " +
                        "userId=${session.userId}, sessionId=${session.sessionId.value}, " +
                        "cause=${cause.message}, refundError=${refundError.message}"
                }
                Mono.empty()
            }

    /**
     * 사용자 취소 로깅.
     * 클라이언트 연결 종료로 스트림이 중단된 경우, 크레딧은 유지하고 로그만 기록.
     *
     * @param session 대화 세션
     * @return 로깅 완료
     */
    private fun logUserCancellation(session: ConversationSession): Mono<Void> {
        logger.info {
            "Conversation cancelled by user - credit kept - userId=${session.userId}, sessionId=${session.sessionId.value}"
        }
        return Mono.empty()
    }
}
