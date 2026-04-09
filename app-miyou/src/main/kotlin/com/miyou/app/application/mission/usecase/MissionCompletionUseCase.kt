package com.miyou.app.application.mission.usecase

import com.miyou.app.domain.dialogue.model.UserId
import com.miyou.app.domain.mission.model.MissionId
import com.miyou.app.domain.mission.model.UserMission
import reactor.core.publisher.Mono

interface MissionCompletionUseCase {
    fun completeMission(
        userId: UserId,
        missionId: MissionId,
    ): Mono<UserMission>
}
