package com.miyou.app.domain.mission.exception

import com.miyou.app.exception.ErrorCategory
import com.miyou.app.exception.ErrorCode

/**
 * 미션 관련 에러 코드.
 */
enum class MissionErrorCode(
    override val code: String,
    override val category: ErrorCategory,
    override val message: String,
) : ErrorCode {
    MISSION_NOT_FOUND(
        "MISSION_NOT_FOUND",
        ErrorCategory.NOT_FOUND,
        "요청한 미션을 찾을 수 없습니다.",
    ),
    MISSION_ALREADY_COMPLETED(
        "MISSION_ALREADY_COMPLETED",
        ErrorCategory.CONFLICT,
        "이미 완료된 미션입니다.",
    ),
    MISSION_REWARD_FAILED(
        "MISSION_REWARD_FAILED",
        ErrorCategory.INTERNAL,
        "미션 보상 지급에 실패했습니다.",
    ),
}
