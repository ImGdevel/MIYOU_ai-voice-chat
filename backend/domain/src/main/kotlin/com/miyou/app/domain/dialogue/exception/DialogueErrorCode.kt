package com.miyou.app.domain.dialogue.exception

import com.miyou.app.exception.ErrorCategory
import com.miyou.app.exception.ErrorCode

/**
 * 대화 관련 에러 코드.
 */
enum class DialogueErrorCode(
    override val code: String,
    override val category: ErrorCategory,
    override val message: String,
) : ErrorCode {
    PERSONA_NOT_FOUND(
        "PERSONA_NOT_FOUND",
        ErrorCategory.NOT_FOUND,
        "요청한 페르소나를 찾을 수 없습니다.",
    ),
    INVALID_SESSION_STATE(
        "INVALID_SESSION_STATE",
        ErrorCategory.INVALID_INPUT,
        "유효하지 않은 세션 상태입니다.",
    ),
    INVALID_AUDIO_FILE(
        "INVALID_AUDIO_FILE",
        ErrorCategory.INVALID_INPUT,
        "오디오 파일만 업로드해 주세요.",
    ),
    AUDIO_TOO_SHORT(
        "AUDIO_TOO_SHORT",
        ErrorCategory.INVALID_INPUT,
        "음성이 너무 짧습니다. 조금 더 길게 말한 뒤 전송해 주세요.",
    ),
    AUDIO_FILE_TOO_LARGE(
        "AUDIO_FILE_TOO_LARGE",
        ErrorCategory.PAYLOAD_TOO_LARGE,
        "음성 파일 크기가 너무 큽니다.",
    ),
    AUDIO_PROCESSING_FAILED(
        "AUDIO_PROCESSING_FAILED",
        ErrorCategory.INTERNAL,
        "음성 처리 중 오류가 발생했습니다.",
    ),
    STT_FAILED(
        "STT_FAILED",
        ErrorCategory.INTERNAL,
        "음성 인식에 실패했습니다.",
    ),
    LLM_PROCESSING_FAILED(
        "LLM_PROCESSING_FAILED",
        ErrorCategory.INTERNAL,
        "응답 생성 중 오류가 발생했습니다.",
    ),
    TTS_FAILED(
        "TTS_FAILED",
        ErrorCategory.INTERNAL,
        "음성 합성에 실패했습니다.",
    ),
}
