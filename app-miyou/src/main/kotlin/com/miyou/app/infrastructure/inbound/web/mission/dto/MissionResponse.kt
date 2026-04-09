package com.miyou.app.infrastructure.inbound.web.mission.dto

import com.miyou.app.domain.mission.model.Mission

data class MissionResponse(
    val missionId: String,
    val type: String,
    val name: String,
    val description: String?,
    val rewardAmount: Long,
    val repeatable: Boolean,
) {
    companion object {
        fun from(mission: Mission): MissionResponse =
            MissionResponse(
                mission.missionId.value,
                mission.type.name,
                mission.name,
                mission.description,
                mission.rewardAmount,
                mission.repeatable,
            )
    }
}
