package com.study.webflux.rag.domain.dialogue.model;

import java.util.Objects;

import com.study.webflux.rag.domain.voice.model.AudioFormat;

/** 오디오 스트리밍 대화 파이프라인 실행 입력입니다. */
public record ExecuteAudioDialogueCommand(
	ConversationSession session,
	String text,
	AudioFormat audioFormat
) {
	public ExecuteAudioDialogueCommand {
		Objects.requireNonNull(session, "session must not be null");
		if (text == null || text.isBlank()) {
			throw new IllegalArgumentException("text must not be blank");
		}
		text = text.trim();
	}
}
