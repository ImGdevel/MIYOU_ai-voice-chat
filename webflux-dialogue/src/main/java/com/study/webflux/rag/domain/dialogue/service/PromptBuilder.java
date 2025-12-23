package com.study.webflux.rag.domain.dialogue.service;

import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.stereotype.Component;

import com.study.webflux.rag.domain.dialogue.model.ConversationContext;
import com.study.webflux.rag.domain.dialogue.port.PromptTemplatePort;
import com.study.webflux.rag.domain.retrieval.model.RetrievalContext;
import com.study.webflux.rag.infrastructure.common.template.FileBasedPromptTemplate;

@Component
public class PromptBuilder implements PromptTemplatePort {

	private static final String CONVERSATION_TEMPLATE = "dialogue/conversation";
	private final FileBasedPromptTemplate templateLoader;

	public PromptBuilder(FileBasedPromptTemplate templateLoader) {
		this.templateLoader = templateLoader;
	}

	@Override
	public String buildPrompt(RetrievalContext context) {
		String contextText = context.isEmpty()
			? ""
			: context.documents().stream().map(doc -> doc.content())
				.collect(Collectors.joining("\n"));

		return templateLoader.load(CONVERSATION_TEMPLATE,
			Map.of("context", contextText, "conversation", ""));
	}

	@Override
	public String buildPromptWithConversation(RetrievalContext context,
		ConversationContext conversationContext) {
		String contextText = context.isEmpty()
			? ""
			: context.documents().stream().map(doc -> doc.content())
				.collect(Collectors.joining("\n"));

		String conversationHistory = buildConversationHistory(conversationContext);

		return templateLoader.load(CONVERSATION_TEMPLATE,
			Map.of("context", contextText, "conversation", conversationHistory));
	}

	@Override
	public String buildDefaultPrompt() {
		return templateLoader.load(CONVERSATION_TEMPLATE,
			Map.of("context", "", "conversation", ""));
	}

	private String buildConversationHistory(ConversationContext conversationContext) {
		if (conversationContext.isEmpty()) {
			return "";
		}

		return conversationContext.turns().stream().filter(turn -> turn.response() != null)
			.map(turn -> String.format("User: %s\nAssistant: %s", turn.query(), turn.response()))
			.collect(Collectors.joining("\n\n"));
	}
}
