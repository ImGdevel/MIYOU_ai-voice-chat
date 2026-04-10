package com.miyou.app.infrastructure.mission.document

import com.miyou.app.domain.dialogue.model.UserId
import com.miyou.app.domain.mission.model.MissionId
import com.miyou.app.domain.mission.model.MissionStatus
import com.miyou.app.domain.mission.model.UserMission
import org.springframework.data.annotation.Id
import org.springframework.data.mongodb.core.index.CompoundIndex
import org.springframework.data.mongodb.core.index.CompoundIndexes
import org.springframework.data.mongodb.core.mapping.Document
import java.time.Instant

@Document(collection = "user_missions")
@CompoundIndexes(
    CompoundIndex(name = "user_mission_idx", def = "{'userId': 1, 'missionId': 1}", unique = true),
)
data class UserMissionDocument(
    @Id val id: String,
    val userId: String,
    val missionId: String,
    val status: String,
    val completedAt: Instant?,
    val rewardedAt: Instant?,
) {
    companion object {
        fun fromDomain(userMission: UserMission): UserMissionDocument {
            val compositeId = "${userMission.userId.value()}:${userMission.missionId.value()}"
            return UserMissionDocument(
                compositeId,
                userMission.userId.value(),
                userMission.missionId.value(),
                userMission.status.name,
                userMission.completedAt,
                userMission.rewardedAt,
            )
        }
    }

    fun toDomain(): UserMission =
        UserMission(
            UserId.of(userId),
            MissionId.of(missionId),
            MissionStatus.valueOf(status),
            completedAt,
            rewardedAt,
        )
}
