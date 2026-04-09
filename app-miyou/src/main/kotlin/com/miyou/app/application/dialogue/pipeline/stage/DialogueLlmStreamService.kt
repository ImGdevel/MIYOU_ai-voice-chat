package com.miyou.app.application.dialogue.pipeline.stage

import com.miyou.app.application.dialogue.pipeline.PipelineInputs
import com.miyou.app.application.dialogue.policy.DialogueExecutionPolicy
import com.miyou.app.application.monitoring.context.PipelineContext
import com.miyou.app.application.monitoring.service.PipelineTracer
import com.miyou.app.domain.dialogue.model.CompletionRequest
import com.miyou.app.domain.dialogue.port.LlmPort
import com.miyou.app.domain.monitoring.model.DialoguePipelineStage
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import reactor.core.scheduler.Schedulers

@Service
class DialogueLlmStreamService(
    private val llmPort: LlmPort,
    private val pipelineTracer: PipelineTracer,
    private val messageService: DialogueMessageService,
    policy: DialogueExecutionPolicy,
) {
    private val logger = LoggerFactory.getLogger(DialogueLlmStreamService::class.java)
    private val llmModel: String = policy.llmModel

    fun buildLlmTokenStream(inputsMono: Mono<PipelineInputs>): Flux<String> =
        streamLlmTokens(inputsMono)
            .subscribeOn(Schedulers.boundedElastic())
            .transform(::trackLlmTokens)

    private fun streamLlmTokens(inputsMono: Mono<PipelineInputs>): Flux<String> =
        Flux.deferContextual { contextView ->
            val tracker = PipelineContext.findTracker(contextView)
            val metadata: Map<String, Any> =
                tracker?.let {
                    mapOf("correlationId" to it.pipelineId())
                } ?: emptyMap()
            inputsMono.flatMapMany { inputs ->
                pipelineTracer
                    .tracePrompt {
                        messageService.buildMessages(
                            inputs.session.personaId,
                            inputs.retrievalContext,
                            inputs.memories,
                            inputs.conversationContext,
                            inputs.currentTurn.query,
                        )
                    }.flatMapMany { messages ->
                        val request =
                            CompletionRequest(
                                messages,
                                llmModel,
                                true,
                                metadata,
                            )
                        pipelineTracer.traceLlm(request.model) {
                            llmPort.streamCompletion(request)
                        }
                    }
            }
        }

    private fun trackLlmTokens(llmTokens: Flux<String>): Flux<String> =
        pipelineTracer
            .incrementOnNext(
                llmTokens,
                DialoguePipelineStage.LLM_COMPLETION,
                "tokenCount",
                1,
            ).doOnNext { token -> logger.debug("LLM Token: [{}]", token) }
}
