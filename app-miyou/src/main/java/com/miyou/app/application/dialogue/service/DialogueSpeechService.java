package com.miyou.app.application.dialogue.service;

import lombok.RequiredArgsConstructor;

import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.codec.multipart.FilePart;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import com.miyou.app.application.dialogue.policy.SttPolicy;
import com.miyou.app.domain.dialogue.model.AudioTranscriptionInput;
import com.miyou.app.domain.dialogue.model.ConversationSession;
import com.miyou.app.domain.dialogue.port.DialoguePipelineUseCase;
import com.miyou.app.domain.dialogue.port.SttPort;
import reactor.core.publisher.Mono;

/** 음성 입력 기반 STT/대화 기능을 제공합니다. */
@Service
@RequiredArgsConstructor
public class DialogueSpeechService {

	private final SttPort sttPort;
	private final DialoguePipelineUseCase dialoguePipelineUseCase;
	private final SttPolicy sttPolicy;

	/** 음성 파일을 텍스트로 변환합니다. */
	public Mono<String> transcribe(FilePart filePart, String language) {
		return toTranscriptionInput(filePart, language)
			.flatMap(sttPort::transcribe);
	}

	/** 음성 파일을 텍스트로 변환한 뒤 텍스트 응답을 생성합니다. */
	public Mono<SpeechDialogueResult> transcribeAndRespond(ConversationSession session,
		FilePart filePart,
		String language) {
		return transcribe(filePart, language)
			.flatMap(transcription -> dialoguePipelineUseCase
				.executeTextOnly(session, transcription)
				.collectList()
				.map(tokens -> new SpeechDialogueResult(transcription, String.join("", tokens))));
	}

	/** 전사 텍스트와 응답 텍스트를 함께 반환합니다. */
	public record SpeechDialogueResult(
		String transcription,
		String response) {
	}

	private Mono<AudioTranscriptionInput> toTranscriptionInput(FilePart filePart, String language) {
		MediaType contentType = filePart.headers().getContentType();
		if (contentType == null || !"audio".equalsIgnoreCase(contentType.getType())) {
			return Mono.error(new ResponseStatusException(HttpStatus.BAD_REQUEST,
				"오디오 파일만 업로드할 수 있습니다"));
		}

		return DataBufferUtils.join(filePart.content())
			.map(dataBuffer -> {
				try {
					byte[] bytes = new byte[dataBuffer.readableByteCount()];
					dataBuffer.read(bytes);
					validateAudioSize(bytes.length);
					String targetLanguage = normalizeLanguage(language);
					return new AudioTranscriptionInput(filePart.filename(),
						contentType.toString(),
						bytes,
						targetLanguage);
				} finally {
					DataBufferUtils.release(dataBuffer);
				}
			});
	}

	private void validateAudioSize(int size) {
		long maxFileSizeBytes = sttPolicy.maxFileSizeBytes();
		if (size > maxFileSizeBytes) {
			throw new ResponseStatusException(HttpStatus.PAYLOAD_TOO_LARGE,
				"오디오 파일 크기가 허용 범위를 초과했습니다. 최대 " + maxFileSizeBytes + " bytes");
		}
	}

	private String normalizeLanguage(String language) {
		if (language != null && !language.isBlank()) {
			return language;
		}
		String defaultLanguage = sttPolicy.defaultLanguage();
		return (defaultLanguage == null || defaultLanguage.isBlank()) ? null : defaultLanguage;
	}
}
