package com.miyou.app.application.dialogue.pipeline;

import com.miyou.app.domain.dialogue.model.ConversationContext;
import com.miyou.app.domain.dialogue.model.ConversationSession;
import com.miyou.app.domain.dialogue.model.ConversationTurn;
import com.miyou.app.domain.memory.model.MemoryRetrievalResult;
import com.miyou.app.domain.retrieval.model.RetrievalContext;

public record PipelineInputs(
	ConversationSession session,
	RetrievalContext retrievalContext,
	MemoryRetrievalResult memories,
	ConversationContext conversationContext,
	ConversationTurn currentTurn
) {
}
