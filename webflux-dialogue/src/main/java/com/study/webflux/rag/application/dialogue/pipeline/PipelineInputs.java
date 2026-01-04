package com.study.webflux.rag.application.dialogue.pipeline;

import com.study.webflux.rag.domain.dialogue.model.ConversationContext;
import com.study.webflux.rag.domain.dialogue.model.ConversationTurn;
import com.study.webflux.rag.domain.memory.model.MemoryRetrievalResult;
import com.study.webflux.rag.domain.retrieval.model.RetrievalContext;

public record PipelineInputs(
	RetrievalContext retrievalContext,
	MemoryRetrievalResult memories,
	ConversationContext conversationContext,
	ConversationTurn currentTurn
) {
}
