package com.study.webflux.rag.application.dialogue.service;

import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Service;

import com.study.webflux.rag.domain.dialogue.model.ConversationContext;
import com.study.webflux.rag.domain.dialogue.model.Message;
import com.study.webflux.rag.domain.memory.model.MemoryRetrievalResult;
import com.study.webflux.rag.domain.retrieval.model.RetrievalContext;

@Service
public class DialogueMessageService {

	private final SystemPromptService systemPromptService;

	public DialogueMessageService(SystemPromptService systemPromptService) {
		this.systemPromptService = systemPromptService;
	}

	public List<Message> buildMessages(RetrievalContext context,
		MemoryRetrievalResult memories,
		ConversationContext conversationContext,
		String currentQuery) {
		List<Message> messages = new ArrayList<>();

		String fullSystemPrompt = systemPromptService.buildSystemPrompt(context, memories);
		if (fullSystemPrompt == null || fullSystemPrompt.isBlank()) {
			fullSystemPrompt = "You are a helpful assistant.";
		}
		messages.add(Message.system(fullSystemPrompt));

		conversationContext.turns().stream().filter(turn -> turn.response() != null)
			.forEach(turn -> {
				messages.add(Message.user(turn.query()));
				messages.add(Message.assistant(turn.response()));
			});

		messages.add(Message.user(currentQuery));

		return messages;
	}
}
