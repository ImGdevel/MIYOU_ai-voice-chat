package com.study.webflux.rag.domain.dialogue.port;

import com.study.webflux.rag.domain.dialogue.model.ConversationContext;
import com.study.webflux.rag.domain.retrieval.model.RetrievalContext;

public interface PromptTemplatePort {
	String buildPrompt(RetrievalContext context);

	String buildPromptWithConversation(RetrievalContext context,
		ConversationContext conversationContext);

	String buildDefaultPrompt();
}
