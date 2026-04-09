package com.miyou.app.application.mission.usecase

import com.miyou.app.domain.dialogue.model.UserId
import com.miyou.app.domain.mission.model.Mission
import com.miyou.app.domain.mission.model.UserMission
import reactor.core.publisher.Flux

interface MissionQueryUseCase {
    fun getAllMissions(): Flux<Mission>

    fun getUserMissions(userId: UserId): Flux<UserMission>
}
