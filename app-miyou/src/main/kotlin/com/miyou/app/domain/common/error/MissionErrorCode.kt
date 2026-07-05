package com.miyou.app.domain.common.error

import org.springframework.http.HttpStatus

/**
 * 미션 관련 에러 코드.
 */
enum class MissionErrorCode(
    override val code: String,
    override val httpStatus: HttpStatus,
    override val message: String,
) : ErrorCode {
    MISSION_NOT_FOUND(
        "MISSION_NOT_FOUND",
        HttpStatus.NOT_FOUND,
        "요청한 미션을 찾을 수 없습니다.",
    ),
    MISSION_ALREADY_COMPLETED(
        "MISSION_ALREADY_COMPLETED",
        HttpStatus.CONFLICT,
        "이미 완료된 미션입니다.",
    ),
    MISSION_REWARD_FAILED(
        "MISSION_REWARD_FAILED",
        HttpStatus.INTERNAL_SERVER_ERROR,
        "미션 보상 지급에 실패했습니다.",
    ),
}
