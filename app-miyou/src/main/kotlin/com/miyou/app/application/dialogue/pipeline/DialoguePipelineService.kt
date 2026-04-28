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
import java.util.concurrent.CancellationException

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

        return prechargeConversation(session, appendPostProcessing(audioStream, postProcessing))
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

        return prechargeConversation(session, appendPostProcessing(textStream, postProcessing))
    }

    private fun <T> appendPostProcessing(
        mainStream: Flux<T>,
        postProcessing: Mono<Void>,
    ): Flux<T> = mainStream.concatWith(postProcessing.thenMany(Flux.empty()))

    private fun <T> prechargeConversation(
        session: ConversationSession,
        stream: Flux<T>,
    ): Flux<T> =
        Flux.usingWhen<T, CreditTransaction>(
            creditDeductUseCase.deductForConversation(session.userId, session.sessionId),
            { _: CreditTransaction -> stream },
            { _: CreditTransaction -> Mono.empty<Void>() },
            { _: CreditTransaction, exception: Throwable -> refundConversation(session, exception) },
            { _: CreditTransaction ->
                refundConversation(
                    session,
                    CancellationException("conversation stream was cancelled"),
                )
            },
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
}
