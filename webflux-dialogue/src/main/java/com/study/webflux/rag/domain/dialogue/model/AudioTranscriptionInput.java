package com.study.webflux.rag.domain.dialogue.model;

/** STT 변환에 필요한 오디오 입력 정보를 담습니다. */
public record AudioTranscriptionInput(
	String fileName,
	String contentType,
	byte[] audioBytes,
	String language
) {

	public AudioTranscriptionInput {
		if (fileName == null || fileName.isBlank()) {
			throw new IllegalArgumentException("파일 이름은 비어 있을 수 없습니다");
		}
		if (contentType == null || contentType.isBlank()) {
			throw new IllegalArgumentException("콘텐츠 타입은 비어 있을 수 없습니다");
		}
		if (audioBytes == null || audioBytes.length == 0) {
			throw new IllegalArgumentException("오디오 데이터는 비어 있을 수 없습니다");
		}
	}
}
