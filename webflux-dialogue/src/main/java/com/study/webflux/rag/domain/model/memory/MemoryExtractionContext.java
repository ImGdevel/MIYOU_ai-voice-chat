package com.study.webflux.rag.domain.model.memory;

import com.study.webflux.rag.domain.model.conversation.ConversationTurn;
import java.util.List;

public record MemoryExtractionContext(
	List<ConversationTurn> recentConversations,
	List<Memory> existingMemories
) {
	public MemoryExtractionContext {
		if (recentConversations == null) {
			recentConversations = List.of();
		}
		if (existingMemories == null) {
			existingMemories = List.of();
		}
	}

	public static MemoryExtractionContext of(List<ConversationTurn> conversations,
		List<Memory> memories) {
		return new MemoryExtractionContext(conversations, memories);
	}
}
