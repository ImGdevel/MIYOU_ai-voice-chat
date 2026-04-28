package com.miyou.app.application.dialogue.pipeline.stage

import com.miyou.app.application.dialogue.pipeline.PipelineInputs
import com.miyou.app.application.memory.policy.MemoryExtractionPolicy
import com.miyou.app.application.memory.service.MemoryExtractionService
import com.miyou.app.application.monitoring.context.PipelineContext
import com.miyou.app.application.monitoring.port.ConversationMetricsPort
import com.miyou.app.application.monitoring.service.PipelineTracer
import com.miyou.app.domain.dialogue.model.ConversationSessionId
import com.miyou.app.domain.dialogue.model.ConversationTurn
import com.miyou.app.domain.dialogue.port.ConversationRepository
import com.miyou.app.domain.dialogue.port.LlmPort
import com.miyou.app.domain.dialogue.port.TokenUsageProvider
import com.miyou.app.domain.memory.port.ConversationCounterPort
import com.miyou.app.domain.monitoring.model.DialoguePipelineStage
import org.springframework.stereotype.Service
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import reactor.core.scheduler.Schedulers
import reactor.util.context.ContextView

@Service
class DialoguePostProcessingService(
    private val conversationRepository: ConversationRepository,
    private val conversationCounterPort: ConversationCounterPort,
    private val memoryExtractionService: MemoryExtractionService,
    private val llmPort: LlmPort,
    private val pipelineTracer: PipelineTracer,
    private val conversationMetrics: ConversationMetricsPort,
    policy: MemoryExtractionPolicy,
) {
    private val conversationThreshold: Long = policy.conversationThreshold.toLong()

    init {
        require(conversationThreshold > 0) { "conversationThreshold must be greater than 0." }
    }

    fun persistAndExtract(
        inputsMono: Mono<PipelineInputs>,
        sentences: Flux<String>,
    ): Mono<Void> {
        val responseMono = sentences.collectList().map { tokens -> tokens.joinToString(separator = " ") }
        return persistConversationAndMaybeExtract(inputsMono, responseMono)
    }

    fun persistAndExtractText(
        inputsMono: Mono<PipelineInputs>,
        textStream: Flux<String>,
    ): Mono<Void> {
        val responseMono =
            textStream
                .collectList()
                .flatMap { tokens ->
                    Mono.deferContextual { contextView ->
                        recordTokenUsageIfAvailable(contextView)
                        Mono.just(tokens.joinToString(separator = ""))
                    }
                }
        return persistConversationAndMaybeExtract(inputsMono, responseMono)
    }

    private fun persistConversationAndMaybeExtract(
        inputsMono: Mono<PipelineInputs>,
        responseMono: Mono<String>,
    ): Mono<Void> =
        inputsMono
            .flatMap { inputs ->
                val session = inputs.session
                val sessionId = session.sessionId
                responseMono
                    .flatMap { response ->
                        conversationMetrics.recordQueryLength(inputs.currentTurn.query.length)
                        conversationMetrics.recordResponseLength(response.length)
                        persistConversation(inputsMono, response)
                    }.flatMap { conversationCounterPort.increment(sessionId) }
                    .doOnNext { count ->
                        conversationMetrics.recordConversationIncrement()
                        conversationMetrics.recordConversationCount(count)
                    }.filter { count -> count % conversationThreshold == 0L }
                    .flatMap { count -> memoryExtractionService.checkAndExtract(sessionId) }
            }.subscribeOn(Schedulers.boundedElastic())
            .then()

    private fun persistConversation(
        inputsMono: Mono<PipelineInputs>,
        fullResponse: String,
    ): Mono<ConversationTurn> =
        inputsMono.flatMap {
            pipelineTracer.tracePersistence {
                conversationRepository.save(it.currentTurn.withResponse(fullResponse))
            }
        }

    private fun recordTokenUsageIfAvailable(contextView: ContextView) {
        val tokenUsageProvider = llmPort as? TokenUsageProvider ?: return
        val tracker = PipelineContext.findTracker(contextView) ?: return
        val tokenUsage = tokenUsageProvider.getTokenUsage(tracker.pipelineId()) ?: return
        tracker.recordStageAttribute(DialoguePipelineStage.LLM_COMPLETION, "promptTokens", tokenUsage.promptTokens)
        tracker.recordStageAttribute(
            DialoguePipelineStage.LLM_COMPLETION,
            "completionTokens",
            tokenUsage.completionTokens
        )
        tracker.recordStageAttribute(DialoguePipelineStage.LLM_COMPLETION, "totalTokens", tokenUsage.totalTokens)
    }
}
