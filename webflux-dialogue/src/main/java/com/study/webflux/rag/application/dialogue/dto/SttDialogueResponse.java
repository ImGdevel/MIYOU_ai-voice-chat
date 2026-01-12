package com.study.webflux.rag.application.dialogue.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "음성 입력 기반 대화 결과")
public record SttDialogueResponse(
	@Schema(description = "음성에서 추출한 텍스트", example = "오늘 회의 일정 알려줘")
	String transcription,

	@Schema(description = "전사 텍스트를 기반으로 생성한 대화 답변", example = "오늘 회의는 오후 2시에 시작합니다.")
	String response
) {
}
