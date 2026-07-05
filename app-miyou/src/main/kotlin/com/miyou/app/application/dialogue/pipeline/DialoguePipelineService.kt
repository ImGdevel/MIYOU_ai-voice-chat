package com.miyou.app.application.dialogue.pipeline

import com.miyou.app.application.credit.usecase.CreditDeductUseCase
import com.miyou.app.application.dialogue.pipeline.stage.DialogueInputService
import com.miyou.app.application.dialogue.pipeline.stage.DialogueLlmStreamService
import com.miyou.app.application.dialogue.pipeline.stage.DialoguePostProcessingService
import com.miyou.app.application.dialogue.pipeline.stage.DialogueTtsStreamService
import com.miyou.app.application.monitoring.aop.MonitoredPipeline
import com.miyou.app.domain.credit.model.CreditTransaction
import com.miyou.app.domain.dialogue.model.ConversationSession
import com.miyou.app.domain.dialogue.port.DialoguePipelineUseCase
import com.miyou.app.domain.voice.model.AudioFormat
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
    private val creditDeductUseCase: CreditDeductUseCase,
) : DialoguePipelineUseCase {
    private val logger = KotlinLogging.logger {}
    private val defaultAudioFormat: AudioFormat = AudioFormat.MP3

    /**
     * 음성 스트리밍 실행.
     *
     * @param session 대화 세션
     * @param text 사용자 입력 텍스트
     * @param format 음성 포맷 (기본값: MP3)
     * @return 음성 데이터 스트림
     */
    override fun executeAudioStreaming(
        session: ConversationSession,
        text: String,
        format: AudioFormat?,
    ): Flux<ByteArray> {
        val targetFormat = format ?: defaultAudioFormat

        val inputsMono = inputService.prepareInputs(session, text).cache()
        val ttsWarmup: Mono<Void> = ttsStreamService.prepareTtsWarmup()

        val llmTokens: Flux<String> = llmStreamService.buildLlmTokenStream(inputsMono)
        val sentences = ttsStreamService.assembleSentences(llmTokens).cache()
        val audioFlux = ttsStreamService.buildAudioStream(sentences, ttsWarmup, targetFormat, session.personaId)
        val postProcessing = postProcessingService.persistAndExtract(inputsMono, sentences)
        val audioStream = ttsStreamService.traceTtsSynthesis(audioFlux)

        // postProcessing은 크레딧 정책 범위 밖에서 concatWith로 이어붙인다.
        // 대화 저장 실패는 크레딧 환불 트리거가 되어서는 안 된다.
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
    @MonitoredPipeline
    override fun executeTextOnly(
        session: ConversationSession,
        text: String,
    ): Flux<String> {
        val inputsMono = inputService.prepareInputs(session, text).cache()

        val llmTokens = llmStreamService.buildLlmTokenStream(inputsMono)
        val textStream = llmTokens.cache()
        val postProcessing = postProcessingService.persistAndExtractText(inputsMono, textStream)

        // postProcessing은 크레딧 정책 범위 밖에서 concatWith로 이어붙인다.
        // 대화 저장 실패는 크레딧 환불 트리거가 되어서는 안 된다.
        return prechargeConversation(session, textStream)
            .concatWith(postProcessing.thenMany(Flux.empty()))
    }

    /**
     * 대화 응답 스트림을 선차감 정책으로 감싼다.
     *
     * - 서비스 내부 오류(LLM/TTS 실패 등): error 핸들러 → 환불
     * - 사용자 직접 취소(클라이언트 연결 종료): cancel 핸들러 → 차감 유지, 로그만 기록
     *
     * postProcessing(대화 저장, 메모리 추출)은 이 범위 밖에서 실행되므로
     * 저장 실패가 환불을 유발하지 않는다.
     */
    private fun <T> prechargeConversation(
        session: ConversationSession,
        responseStream: Flux<T>,
    ): Flux<T> =
        Flux.usingWhen<T, CreditTransaction>(
            creditDeductUseCase.deductForConversation(session.userId, session.sessionId),
            { _: CreditTransaction -> responseStream },
            { _: CreditTransaction -> Mono.empty<Void>() },
            { _: CreditTransaction, exception: Throwable -> refundConversation(session, exception) },
            { _: CreditTransaction -> logUserCancellation(session) },
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
        creditDeductUseCase
            .refundForConversation(session.userId, session.sessionId)
            .doOnNext { tx ->
                logger.warn {
                    "Conversation credit refunded - " +
                        "userId=${session.userId.value}, sessionId=${session.sessionId.value}, " +
                        "transactionId=${tx.transactionId.value}, cause=${cause.message}"
                }
            }.then()
            .onErrorResume { refundError ->
                logger.error(refundError) {
                    "Conversation credit refund failed - " +
                        "userId=${session.userId.value}, sessionId=${session.sessionId.value}, " +
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
            "Conversation cancelled by user - credit kept - userId=${session.userId.value}, sessionId=${session.sessionId.value}"
        }
        return Mono.empty()
    }
}
