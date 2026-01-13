package com.study.webflux.rag.domain.dialogue.port;

import com.study.webflux.rag.domain.dialogue.model.PersonaId;
import com.study.webflux.rag.domain.dialogue.model.UserId;
import com.study.webflux.rag.domain.voice.model.AudioFormat;
import reactor.core.publisher.Flux;

/**
 * DialoguePipelineUseCase는 대화 파이프라인을 실행하는 인터페이스를 정의합니다.
 */
public interface DialoguePipelineUseCase {
	/**
	 * 대화 파이프라인을 실행하고 오디오 스트림을 반환합니다.
	 *
	 * @param personaId
	 *            페르소나 ID
	 * @param userId
	 *            사용자 ID
	 * @param text
	 *            대화 텍스트
	 * @param format
	 *            오디오 포맷
	 * @return 오디오 스트림
	 */
	Flux<byte[]> executeAudioStreaming(PersonaId personaId,
		UserId userId,
		String text,
		AudioFormat format);

	default Flux<byte[]> executeAudioStreaming(String text, AudioFormat format) {
		return executeAudioStreaming(PersonaId.defaultPersona(), UserId.generate(), text, format);
	}

	default Flux<byte[]> executeAudioStreaming(UserId userId, String text, AudioFormat format) {
		return executeAudioStreaming(PersonaId.defaultPersona(), userId, text, format);
	}

	default Flux<byte[]> executeAudioStreaming(UserId userId, String text) {
		return executeAudioStreaming(PersonaId.defaultPersona(), userId, text, null);
	}

	/**
	 * 대화 파이프라인을 실행하고 텍스트 스트림을 반환합니다.
	 *
	 * @param personaId
	 *            페르소나 ID
	 * @param userId
	 *            사용자 ID
	 * @param text
	 *            대화 텍스트
	 * @return 텍스트 스트림
	 */
	Flux<String> executeTextOnly(PersonaId personaId, UserId userId, String text);

	default Flux<String> executeTextOnly(UserId userId, String text) {
		return executeTextOnly(PersonaId.defaultPersona(), userId, text);
	}

	default Flux<String> executeTextOnly(String text) {
		return executeTextOnly(PersonaId.defaultPersona(), UserId.generate(), text);
	}
}
