package com.study.webflux.rag.domain.memory.model;

import java.util.List;

import com.study.webflux.rag.domain.dialogue.model.ConversationTurn;
import com.study.webflux.rag.domain.dialogue.model.UserId;

public record MemoryExtractionContext(
	UserId userId,
	List<ConversationTurn> recentConversations,
	List<Memory> existingMemories
) {
	public MemoryExtractionContext {
		if (userId == null) {
			throw new IllegalArgumentException("userId cannot be null");
		}
		if (recentConversations == null) {
			recentConversations = List.of();
		}
		if (existingMemories == null) {
			existingMemories = List.of();
		}
	}

	public static MemoryExtractionContext of(UserId userId,
		List<ConversationTurn> conversations,
		List<Memory> memories) {
		return new MemoryExtractionContext(userId, conversations, memories);
	}
}
