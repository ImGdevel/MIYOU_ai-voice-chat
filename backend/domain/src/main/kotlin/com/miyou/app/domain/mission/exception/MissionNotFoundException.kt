package com.miyou.app.domain.mission.exception

import com.miyou.app.exception.BusinessException

class MissionNotFoundException(
    val missionId: String,
) : BusinessException(
        errorCode = MissionErrorCode.MISSION_NOT_FOUND,
        message = "${MissionErrorCode.MISSION_NOT_FOUND.message} ($missionId)",
        details = mapOf("missionId" to missionId)
    )
