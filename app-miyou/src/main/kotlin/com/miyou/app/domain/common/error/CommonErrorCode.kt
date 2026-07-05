package com.miyou.app.domain.common.error

import org.springframework.http.HttpStatus

/**
 * 공통 에러 코드.
 */
enum class CommonErrorCode(
    override val code: String,
    override val httpStatus: HttpStatus,
    override val message: String,
) : ErrorCode {
    INVALID_REQUEST(
        "INVALID_REQUEST",
        HttpStatus.BAD_REQUEST,
        "잘못된 요청입니다.",
    ),
    UNSUPPORTED_AUDIO_FORMAT(
        "UNSUPPORTED_AUDIO_FORMAT",
        HttpStatus.BAD_REQUEST,
        "지원하지 않는 오디오 포맷입니다.",
    ),
    RESOURCE_NOT_FOUND(
        "RESOURCE_NOT_FOUND",
        HttpStatus.NOT_FOUND,
        "요청한 리소스를 찾을 수 없습니다.",
    ),
    SESSION_NOT_FOUND(
        "SESSION_NOT_FOUND",
        HttpStatus.NOT_FOUND,
        "해당 대화 세션을 찾을 수 없습니다.",
    ),
    MISSION_NOT_FOUND(
        "MISSION_NOT_FOUND",
        HttpStatus.NOT_FOUND,
        "해당 미션을 찾을 수 없습니다.",
    ),
    CONFLICT(
        "CONFLICT",
        HttpStatus.CONFLICT,
        "요청이 현재 상태와 충돌합니다.",
    ),
    ALREADY_COMPLETED_MISSION(
        "ALREADY_COMPLETED_MISSION",
        HttpStatus.CONFLICT,
        "이미 완료된 미션입니다.",
    ),
    INTERNAL_SERVER_ERROR(
        "INTERNAL_SERVER_ERROR",
        HttpStatus.INTERNAL_SERVER_ERROR,
        "서버 오류가 발생했습니다.",
    ),
}
