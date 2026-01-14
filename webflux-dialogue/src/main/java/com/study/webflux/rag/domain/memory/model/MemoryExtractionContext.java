package com.study.webflux.rag.domain.memory.model;

import java.util.List;

import com.study.webflux.rag.domain.dialogue.model.ConversationSessionId;
import com.study.webflux.rag.domain.dialogue.model.ConversationTurn;

public record MemoryExtractionContext(
	ConversationSessionId sessionId,
	List<ConversationTurn> recentConversations,
	List<Memory> existingMemories
) {
	public MemoryExtractionContext {
		if (sessionId == null) {
			throw new IllegalArgumentException("sessionId cannot be null");
		}
		if (recentConversations == null) {
			recentConversations = List.of();
		}
		if (existingMemories == null) {
			existingMemories = List.of();
		}
	}

	public static MemoryExtractionContext of(ConversationSessionId sessionId,
		List<ConversationTurn> conversations,
		List<Memory> memories) {
		return new MemoryExtractionContext(sessionId, conversations, memories);
	}
}
