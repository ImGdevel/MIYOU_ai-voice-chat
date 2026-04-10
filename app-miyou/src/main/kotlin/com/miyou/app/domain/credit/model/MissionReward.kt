package com.miyou.app.domain.credit.model

import com.miyou.app.domain.mission.model.MissionId

data class MissionReward(
    val missionId: MissionId,
    val missionType: String,
) : CreditSource {
    override fun sourceType(): CreditSourceType = CreditSourceType.MISSION_REWARD

    fun missionId(): MissionId = missionId

    fun missionType(): String = missionType
}
