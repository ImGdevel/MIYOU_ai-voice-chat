package com.miyou.app.infrastructure.inbound.web.dialogue.dto

import io.swagger.v3.oas.annotations.media.Schema

@Schema(description = "STT 변환 결과")
data class SttTranscriptionResponse(
    @field:Schema(description = "요청한 오디오에서 추출된 문자", example = "안녕하세요, 어떻게 도와드릴까요?")
    val transcription: String,
)
