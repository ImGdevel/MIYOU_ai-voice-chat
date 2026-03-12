package com.study.webflux.rag.application.dialogue.pipeline.stage;

import java.util.ArrayList;
import java.util.List;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Service;

import com.study.webflux.rag.application.dialogue.pipeline.DialogueMessageCommand;
import com.study.webflux.rag.domain.dialogue.model.ConversationContext;
import com.study.webflux.rag.domain.dialogue.model.Message;

@Service
@RequiredArgsConstructor
public class DialogueMessageService {

	private static final String DEFAULT_SYSTEM_PROMPT = "You are a helpful assistant.";

	private final SystemPromptService systemPromptService;

	/**
	 * 시스템 프롬프트와 이전 대화, 현재 사용자 쿼리를 조합해 LLM 요청 메시지 목록을 만듭니다.
	 */
	public List<Message> buildMessages(DialogueMessageCommand command) {
		List<Message> messages = new ArrayList<>();

		String fullSystemPrompt = systemPromptService.buildSystemPrompt(
			new com.study.webflux.rag.application.dialogue.pipeline.SystemPromptContext(
				command.personaId(),
				command.retrievalContext(),
				command.memoryResult()));
		if (fullSystemPrompt == null || fullSystemPrompt.isBlank()) {
			fullSystemPrompt = DEFAULT_SYSTEM_PROMPT;
		}
		messages.add(Message.system(fullSystemPrompt));

		ConversationContext conversationContext = command.conversationContext();
		conversationContext.turns().stream()
			.filter(turn -> turn.response() != null)
			.forEach(turn -> {
				messages.add(Message.user(turn.query()));
				messages.add(Message.assistant(turn.response()));
			});

		messages.add(Message.user(command.currentQuery()));

		return messages;
	}
}
