package com.miyou.app.domain.mission.exception

import com.miyou.app.exception.BusinessException

class MissionAlreadyCompletedException(
    val missionId: String,
) : BusinessException(
        errorCode = MissionErrorCode.MISSION_ALREADY_COMPLETED,
        message = "${MissionErrorCode.MISSION_ALREADY_COMPLETED.message} ($missionId)",
        details = mapOf("missionId" to missionId)
    )
