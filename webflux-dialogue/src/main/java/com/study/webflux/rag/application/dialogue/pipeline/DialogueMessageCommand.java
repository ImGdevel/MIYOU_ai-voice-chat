package com.study.webflux.rag.application.dialogue.pipeline;

import com.study.webflux.rag.domain.dialogue.model.ConversationContext;
import com.study.webflux.rag.domain.dialogue.model.PersonaId;
import com.study.webflux.rag.domain.memory.model.MemoryRetrievalResult;
import com.study.webflux.rag.domain.retrieval.model.RetrievalContext;

/** LLM 메시지 조립 입력입니다. */
public record DialogueMessageCommand(
	PersonaId personaId,
	RetrievalContext retrievalContext,
	MemoryRetrievalResult memoryResult,
	ConversationContext conversationContext,
	String currentQuery
) {
	public DialogueMessageCommand {
		personaId = personaId == null ? PersonaId.defaultPersona() : personaId;
		conversationContext = conversationContext == null
			? ConversationContext.empty()
			: conversationContext;
		if (currentQuery == null || currentQuery.isBlank()) {
			throw new IllegalArgumentException("currentQuery must not be blank");
		}
		currentQuery = currentQuery.trim();
	}
}
