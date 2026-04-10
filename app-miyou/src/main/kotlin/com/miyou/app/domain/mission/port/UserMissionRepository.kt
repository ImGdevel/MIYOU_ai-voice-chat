package com.miyou.app.domain.mission.port

import com.miyou.app.domain.dialogue.model.UserId
import com.miyou.app.domain.mission.model.MissionId
import com.miyou.app.domain.mission.model.UserMission
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono

interface UserMissionRepository {
    fun findByUserId(userId: UserId): Flux<UserMission>

    fun findByUserIdAndMissionId(
        userId: UserId,
        missionId: MissionId,
    ): Mono<UserMission>

    fun save(userMission: UserMission): Mono<UserMission>
}
