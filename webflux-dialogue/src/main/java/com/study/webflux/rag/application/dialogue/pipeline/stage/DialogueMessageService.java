package com.study.webflux.rag.application.dialogue.pipeline.stage;

import java.util.ArrayList;
import java.util.List;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Service;

import com.study.webflux.rag.domain.dialogue.model.ConversationContext;
import com.study.webflux.rag.domain.dialogue.model.Message;
import com.study.webflux.rag.domain.memory.model.MemoryRetrievalResult;
import com.study.webflux.rag.domain.retrieval.model.RetrievalContext;

@Service
@RequiredArgsConstructor
public class DialogueMessageService {

	private final SystemPromptService systemPromptService;

	/**
	 * 시스템 프롬프트와 이전 대화, 현재 사용자 쿼리를 조합해 LLM 요청 메시지 목록을 만듭니다.
	 */
	public List<Message> buildMessages(RetrievalContext context,
		MemoryRetrievalResult memories,
		ConversationContext conversationContext,
		String currentQuery) {
		if (currentQuery == null || currentQuery.isBlank()) {
			throw new IllegalArgumentException("현재 사용자 쿼리는 null이거나 비어 있을 수 없습니다.");
		}

		List<Message> messages = new ArrayList<>();

		String fullSystemPrompt = systemPromptService.buildSystemPrompt(context, memories);
		if (fullSystemPrompt == null || fullSystemPrompt.isBlank()) {
			fullSystemPrompt = "You are a helpful assistant.";
		}
		messages.add(Message.system(fullSystemPrompt));

		conversationContext.turns().stream()
			.filter(turn -> turn.response() != null)
			.forEach(turn -> {
				messages.add(Message.user(turn.query()));
				messages.add(Message.assistant(turn.response()));
			});

		messages.add(Message.user(currentQuery));

		return messages;
	}
}
