package com.miyou.app.application.mission.usecase

import com.miyou.app.domain.mission.model.MissionId
import com.miyou.app.domain.mission.model.UserMission
import reactor.core.publisher.Mono

interface MissionCompletionUseCase {
    fun completeMission(
        userId: String,
        missionId: MissionId,
    ): Mono<UserMission>
}
