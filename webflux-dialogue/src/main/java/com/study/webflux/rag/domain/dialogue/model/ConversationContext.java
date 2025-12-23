package com.study.webflux.rag.domain.dialogue.model;

import java.util.List;

public record ConversationContext(
	List<com.study.webflux.rag.domain.dialogue.model.ConversationTurn> turns
) {
	public ConversationContext {
		if (turns == null) {
			turns = List.of();
		}
	}

	public boolean isEmpty() {
		return turns.isEmpty();
	}

	public int size() {
		return turns.size();
	}

	public static ConversationContext empty() {
		return new ConversationContext(List.of());
	}

	public static ConversationContext of(
		List<com.study.webflux.rag.domain.dialogue.model.ConversationTurn> turns) {
		return new ConversationContext(turns);
	}
}
