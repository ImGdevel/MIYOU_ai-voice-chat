package com.miyou.app.infrastructure.inbound.web.dialogue.dto

import io.swagger.v3.oas.annotations.media.Schema

@Schema(description = "STT 입력 결과 응답")
data class SttDialogueResponse(
    @field:Schema(description = "요청한 오디오에서 추출된 문자", example = "안녕하세요, 어떻게 도와드릴까요?")
    val transcription: String,
    @field:Schema(description = "STT 전송 후 생성된 응답", example = "안녕하세요! 무엇을 도와드릴까요?")
    val response: String,
)
