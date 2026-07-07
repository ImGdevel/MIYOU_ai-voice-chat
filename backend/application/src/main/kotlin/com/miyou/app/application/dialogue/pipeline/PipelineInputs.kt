package com.miyou.app.application.dialogue.pipeline

import com.miyou.app.domain.dialogue.model.ConversationContext
import com.miyou.app.domain.dialogue.model.ConversationSession
import com.miyou.app.domain.dialogue.model.ConversationTurn
import com.miyou.app.domain.memory.model.MemoryRetrievalResult
import com.miyou.app.domain.retrieval.model.RetrievalContext

data class PipelineInputs(
    val session: ConversationSession,
    val retrievalContext: RetrievalContext,
    val memories: MemoryRetrievalResult,
    val conversationContext: ConversationContext,
    val currentTurn: ConversationTurn,
)
