package com.study.webflux.rag.application.dialogue.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "STT 변환 결과")
public record SttTranscriptionResponse(
	@Schema(description = "음성에서 추출한 텍스트", example = "오늘 회의 일정 알려줘")
	String transcription
) {
}
