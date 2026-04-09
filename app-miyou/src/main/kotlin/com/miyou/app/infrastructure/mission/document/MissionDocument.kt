package com.miyou.app.infrastructure.mission.document

import com.miyou.app.domain.mission.model.Mission
import com.miyou.app.domain.mission.model.MissionId
import com.miyou.app.domain.mission.model.MissionType
import org.springframework.data.annotation.Id
import org.springframework.data.mongodb.core.index.Indexed
import org.springframework.data.mongodb.core.mapping.Document

@Document(collection = "missions")
data class MissionDocument(
    @Id val id: String,
    @Indexed val type: String,
    val name: String,
    val description: String?,
    val rewardAmount: Long,
    val repeatable: Boolean,
) {
    companion object {
        fun fromDomain(mission: Mission): MissionDocument =
            MissionDocument(
                mission.missionId.value,
                mission.type.name,
                mission.name,
                mission.description,
                mission.rewardAmount,
                mission.repeatable,
            )
    }

    fun toDomain(): Mission =
        Mission(
            MissionId.of(id),
            MissionType.valueOf(type),
            name,
            description,
            rewardAmount,
            repeatable,
        )
}
