package com.miyou.app.api.dialogue.dto

import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Pattern
import jakarta.validation.constraints.Size

data class CreateSessionRequest(
    @field:NotBlank(message = "userId is required")
    @field:Size(max = 128, message = "userId too long")
    val userId: String = "",
    // null/빈 문자열 둘 다 PersonaId.ofNullable()에서 기본 페르소나로 처리되므로 허용해야
    // 한다 (그래서 정규식이 `+`가 아니라 `*`) - 타입도 String?이어야 클라이언트가
    // "personaId": null을 명시적으로 보냈을 때 Jackson 역직렬화 단계에서 예외 없이
    // 통과한다 (non-null String 필드에 명시적 null이 오면 역직렬화 자체가 실패한다).
    // Bean Validation의 @Size/@Pattern은 null을 항상 유효한 것으로 취급하므로 null이
    // 와도 그대로 검증을 통과해 도메인으로 넘어간다.
    // PersonaId의 require(length<=64, 정규식)와 반드시 동기화 상태를 유지한다.
    @field:Size(max = 64, message = "personaId too long")
    @field:Pattern(regexp = "^[a-zA-Z0-9_-]*$", message = "personaId contains invalid characters")
    val personaId: String? = null,
)
