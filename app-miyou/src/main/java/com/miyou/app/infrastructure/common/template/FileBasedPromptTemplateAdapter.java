package com.miyou.app.infrastructure.common.template;

import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

import com.miyou.app.domain.dialogue.model.ConversationContext;
import com.miyou.app.domain.dialogue.port.PromptTemplatePort;
import com.miyou.app.domain.retrieval.model.RetrievalContext;

@Primary
@Component
public class FileBasedPromptTemplateAdapter implements PromptTemplatePort {

	private static final String CONVERSATION_TEMPLATE = "dialogue/conversation";
	private final FileBasedPromptTemplate templateLoader;

	public FileBasedPromptTemplateAdapter(FileBasedPromptTemplate templateLoader) {
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
