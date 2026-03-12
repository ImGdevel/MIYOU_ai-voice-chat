package com.study.webflux.rag.domain.dialogue.model;

import java.util.Objects;

/** 텍스트 전용 대화 파이프라인 실행 입력입니다. */
public record ExecuteTextDialogueCommand(
	ConversationSession session,
	String text
) {
	public ExecuteTextDialogueCommand {
		Objects.requireNonNull(session, "session must not be null");
		if (text == null || text.isBlank()) {
			throw new IllegalArgumentException("text must not be blank");
		}
		text = text.trim();
	}
}
