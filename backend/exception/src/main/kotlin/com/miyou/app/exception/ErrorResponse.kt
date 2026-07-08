package com.miyou.app.exception

import java.time.LocalDateTime

/**
 * API 에러 응답.
 *
 * @param code 에러 코드
 * @param message 에러 메시지 (한국어)
 * @param timestamp 발생 시각
 * @param path 요청 경로
 */
data class ErrorResponse(
    val code: String,
    val message: String,
    val timestamp: LocalDateTime = LocalDateTime.now(),
    val path: String? = null,
    val details: Map<String, Any>? = null,
)
