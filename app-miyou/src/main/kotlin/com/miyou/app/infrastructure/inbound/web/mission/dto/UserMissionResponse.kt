package com.miyou.app.infrastructure.inbound.web.mission.dto

import com.miyou.app.domain.mission.model.UserMission
import java.time.Instant

data class UserMissionResponse(
    val userId: String,
    val missionId: String,
    val status: String,
    val completedAt: Instant?,
    val rewardedAt: Instant?,
) {
    companion object {
        fun from(userMission: UserMission): UserMissionResponse =
            UserMissionResponse(
                userMission.userId.value,
                userMission.missionId.value,
                userMission.status.name,
                userMission.completedAt,
                userMission.rewardedAt,
            )
    }
}
