package com.miyou.app.domain.mission.model

import java.time.Instant

data class UserMission(
    val userId: String,
    val missionId: MissionId,
    val status: MissionStatus,
    val completedAt: Instant?,
    val rewardedAt: Instant?,
) {
    companion object {
        @JvmStatic
        fun start(
            userId: String,
            missionId: MissionId,
        ): UserMission = UserMission(userId, missionId, MissionStatus.AVAILABLE, null, null)
    }

    fun complete(): UserMission = copy(status = MissionStatus.COMPLETED, completedAt = Instant.now())

    fun reward(): UserMission = copy(status = MissionStatus.REWARDED, rewardedAt = Instant.now())

    fun userId(): String = userId

    fun missionId(): MissionId = missionId

    fun status(): MissionStatus = status

    fun completedAt(): Instant? = completedAt

    fun rewardedAt(): Instant? = rewardedAt
}
