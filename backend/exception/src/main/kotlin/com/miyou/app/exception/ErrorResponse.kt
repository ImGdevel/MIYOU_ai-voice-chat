package com.miyou.app.exception

import io.swagger.v3.oas.annotations.media.Schema
import java.time.Instant

/**
 * API 에러 응답.
 *
 * @param code 에러 코드
 * @param message 에러 메시지 (한국어)
 * @param timestamp 발생 시각
 * @param path 요청 경로
 */
@Schema(description = "API 에러 공통 응답 DTO")
data class ErrorResponse(
    @field:Schema(description = "비즈니스 에러 코드", example = "INSUFFICIENT_CREDIT")
    val code: String,
    @field:Schema(description = "에러 상세 메시지 (한국어)", example = "크레딧이 부족합니다. 잔액을 충전해주세요.")
    val message: String,
    @field:Schema(description = "에러 발생 시각", example = "2026-07-08T16:30:21.124Z")
    val timestamp: Instant = Instant.now(),
    @field:Schema(description = "요청 API 경로", example = "/api/v1/rag/dialogue/audio")
    val path: String? = null,
    @field:Schema(
        description = "비즈니스 관련 추가 세부 데이터 (필드별 검증 오류 정보 또는 크레딧 상태 등)",
        example = "{\"userId\": \"user-123\", \"requiredAmount\": 10, \"currentBalance\": 5}"
    )
    val details: Map<String, Any>? = null,
)
