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
    INVALID_AUDIO_FILE(
        "INVALID_AUDIO_FILE",
        HttpStatus.BAD_REQUEST,
        "오디오 파일만 업로드해 주세요.",
    ),
    AUDIO_TOO_SHORT(
        "AUDIO_TOO_SHORT",
        HttpStatus.BAD_REQUEST,
        "음성이 너무 짧습니다. 조금 더 길게 말한 뒤 전송해 주세요.",
    ),
    AUDIO_FILE_TOO_LARGE(
        "AUDIO_FILE_TOO_LARGE",
        HttpStatus.PAYLOAD_TOO_LARGE,
        "음성 파일 크기가 너무 큽니다.",
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
