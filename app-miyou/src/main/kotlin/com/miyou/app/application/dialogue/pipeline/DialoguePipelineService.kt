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
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono

@Service
class DialoguePipelineService(
    private val inputService: DialogueInputService,
    private val llmStreamService: DialogueLlmStreamService,
    private val ttsStreamService: DialogueTtsStreamService,
    private val postProcessingService: DialoguePostProcessingService,
    private val creditDeductUseCase: CreditDeductUseCase,
) : DialoguePipelineUseCase {
    private val logger = LoggerFactory.getLogger(DialoguePipelineService::class.java)
    private val defaultAudioFormat: AudioFormat = AudioFormat.MP3

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

    private fun refundConversation(
        session: ConversationSession,
        cause: Throwable,
    ): Mono<Void> =
        creditDeductUseCase
            .refundForConversation(session.userId, session.sessionId)
            .doOnNext { tx ->
                logger.warn(
                    "Conversation credit refunded - userId={}, sessionId={}, transactionId={}, cause={}",
                    session.userId.value,
                    session.sessionId.value,
                    tx.transactionId.value,
                    cause.message,
                )
            }.then()
            .onErrorResume { refundError ->
                logger.error(
                    "Conversation credit refund failed - userId={}, sessionId={}, cause={}, refundError={}",
                    session.userId.value,
                    session.sessionId.value,
                    cause.message,
                    refundError.message,
                )
                Mono.empty()
            }

    private fun logUserCancellation(session: ConversationSession): Mono<Void> {
        logger.info(
            "Conversation cancelled by user - credit kept - userId={}, sessionId={}",
            session.userId.value,
            session.sessionId.value,
        )
        return Mono.empty()
    }
}
