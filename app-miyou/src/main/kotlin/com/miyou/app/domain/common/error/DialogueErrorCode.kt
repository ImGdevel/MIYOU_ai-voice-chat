package com.miyou.app.domain.common.error

import org.springframework.http.HttpStatus

/**
 * 대화 관련 에러 코드.
 */
enum class DialogueErrorCode(
    override val code: String,
    override val httpStatus: HttpStatus,
    override val message: String,
) : ErrorCode {
    PERSONA_NOT_FOUND(
        "PERSONA_NOT_FOUND",
        HttpStatus.NOT_FOUND,
        "요청한 페르소나를 찾을 수 없습니다.",
    ),
    INVALID_SESSION_STATE(
        "INVALID_SESSION_STATE",
        HttpStatus.BAD_REQUEST,
        "유효하지 않은 세션 상태입니다.",
    ),
    AUDIO_PROCESSING_FAILED(
        "AUDIO_PROCESSING_FAILED",
        HttpStatus.INTERNAL_SERVER_ERROR,
        "음성 처리 중 오류가 발생했습니다.",
    ),
    STT_FAILED(
        "STT_FAILED",
        HttpStatus.INTERNAL_SERVER_ERROR,
        "음성 인식에 실패했습니다.",
    ),
    LLM_PROCESSING_FAILED(
        "LLM_PROCESSING_FAILED",
        HttpStatus.INTERNAL_SERVER_ERROR,
        "응답 생성 중 오류가 발생했습니다.",
    ),
    TTS_FAILED(
        "TTS_FAILED",
        HttpStatus.INTERNAL_SERVER_ERROR,
        "음성 합성에 실패했습니다.",
    ),
}
