package com.miyou.app.domain.dialogue.port;

import com.miyou.app.domain.dialogue.model.ConversationContext;
import com.miyou.app.domain.retrieval.model.RetrievalContext;

public interface PromptTemplatePort {
	String buildPrompt(RetrievalContext context);

	String buildPromptWithConversation(RetrievalContext context,
		ConversationContext conversationContext);

	String buildDefaultPrompt();
}
