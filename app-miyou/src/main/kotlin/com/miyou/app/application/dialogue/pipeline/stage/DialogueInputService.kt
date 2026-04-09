package com.miyou.app.application.dialogue.pipeline.stage

import com.miyou.app.application.dialogue.pipeline.PipelineInputs
import com.miyou.app.application.monitoring.service.PipelineTracer
import com.miyou.app.domain.dialogue.model.ConversationContext
import com.miyou.app.domain.dialogue.model.ConversationSession
import com.miyou.app.domain.dialogue.model.ConversationTurn
import com.miyou.app.domain.dialogue.port.ConversationRepository
import com.miyou.app.domain.memory.model.MemoryRetrievalResult
import com.miyou.app.domain.retrieval.model.RetrievalContext
import com.miyou.app.domain.retrieval.port.RetrievalPort
import org.springframework.stereotype.Service
import reactor.core.publisher.Mono

@Service
class DialogueInputService(
    private val retrievalPort: RetrievalPort,
    private val conversationRepository: ConversationRepository,
    private val pipelineTracer: PipelineTracer,
) {
    fun prepareInputs(
        session: ConversationSession,
        text: String,
    ): Mono<PipelineInputs> {
        val currentTurn =
            Mono
                .fromCallable { ConversationTurn.create(session.sessionId, text) }
                .cache()
        val memories =
            pipelineTracer.traceMemories {
                retrievalPort.retrieveMemories(session.sessionId, text, 5)
            }
        val retrievalContext =
            pipelineTracer.traceRetrieval {
                retrievalPort.retrieve(session.sessionId, text, 3)
            }
        val history = loadConversationHistory(session).cache()

        return Mono
            .zip(retrievalContext, memories, history, currentTurn)
            .map { tuple ->
                PipelineInputs(
                    session,
                    tuple.t1,
                    tuple.t2,
                    tuple.t3,
                    tuple.t4,
                )
            }
    }

    private fun loadConversationHistory(session: ConversationSession): Mono<ConversationContext> =
        conversationRepository
            .findRecent(session.sessionId, 10)
            .collectList()
            .map { turns -> ConversationContext.of(turns) }
            .defaultIfEmpty(ConversationContext.empty())
}
