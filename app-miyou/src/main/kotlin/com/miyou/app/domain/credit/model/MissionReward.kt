package com.miyou.app.domain.credit.model

data class MissionReward(
    val missionId: String,
    val missionType: String,
) : CreditSource {
    override fun sourceType(): CreditSourceType = CreditSourceType.MISSION_REWARD

    fun missionId(): String = missionId

    fun missionType(): String = missionType
}
