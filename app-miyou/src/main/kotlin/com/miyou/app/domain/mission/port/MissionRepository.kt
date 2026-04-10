package com.miyou.app.domain.mission.port

import com.miyou.app.domain.mission.model.Mission
import com.miyou.app.domain.mission.model.MissionId
import com.miyou.app.domain.mission.model.MissionType
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono

interface MissionRepository {
    fun findById(missionId: MissionId): Mono<Mission>

    fun findAll(): Flux<Mission>

    fun findByType(type: MissionType): Flux<Mission>

    fun save(mission: Mission): Mono<Mission>
}
