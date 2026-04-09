package com.miyou.app.fixture

import com.miyou.app.application.dialogue.pipeline.PipelineInputs
import com.miyou.app.domain.dialogue.model.ConversationContext
import com.miyou.app.domain.dialogue.model.ConversationSession
import com.miyou.app.domain.dialogue.model.ConversationTurn
import com.miyou.app.domain.memory.model.MemoryRetrievalResult
import com.miyou.app.domain.retrieval.model.RetrievalContext

object PipelineInputsFixture {
    @JvmStatic
    fun create(): PipelineInputs {
        val session = ConversationSessionFixture.create()
        val turn = ConversationTurnFixture.create(session.sessionId)
        return PipelineInputs(
            session,
            RetrievalContext.empty(turn.query),
            MemoryRetrievalResult.empty(),
            ConversationContext.empty(),
            turn,
        )
    }

    @JvmStatic
    fun create(session: ConversationSession): PipelineInputs {
        val turn = ConversationTurnFixture.create(session.sessionId)
        return PipelineInputs(
            session,
            RetrievalContext.empty(turn.query),
            MemoryRetrievalResult.empty(),
            ConversationContext.empty(),
            turn,
        )
    }

    @JvmStatic
    fun createWithQuery(query: String): PipelineInputs {
        val session = ConversationSessionFixture.create()
        val turn = ConversationTurn.create(session.sessionId, query)
        return PipelineInputs(
            session,
            RetrievalContext.empty(query),
            MemoryRetrievalResult.empty(),
            ConversationContext.empty(),
            turn,
        )
    }

    @JvmStatic
    fun createMinimal(
        session: ConversationSession,
        turn: ConversationTurn,
    ): PipelineInputs =
        PipelineInputs(
            session,
            RetrievalContext.empty(turn.query),
            MemoryRetrievalResult.empty(),
            ConversationContext.empty(),
            turn,
        )
}
