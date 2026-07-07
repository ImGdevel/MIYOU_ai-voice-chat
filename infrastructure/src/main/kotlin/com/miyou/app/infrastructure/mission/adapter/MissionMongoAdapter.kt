package com.miyou.app.infrastructure.mission.adapter

import com.miyou.app.domain.mission.model.Mission
import com.miyou.app.domain.mission.model.MissionId
import com.miyou.app.domain.mission.model.MissionType
import com.miyou.app.domain.mission.port.MissionRepository
import com.miyou.app.infrastructure.mission.document.MissionDocument
import com.miyou.app.infrastructure.mission.repository.MissionMongoRepository
import org.springframework.stereotype.Component
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono

@Component
class MissionMongoAdapter(
    private val mongoRepository: MissionMongoRepository,
) : MissionRepository {
    override fun findById(missionId: MissionId): Mono<Mission> =
        mongoRepository.findById(missionId.value).map(MissionDocument::toDomain)

    override fun findAll(): Flux<Mission> = mongoRepository.findAll().map(MissionDocument::toDomain)

    override fun findByType(type: MissionType): Flux<Mission> =
        mongoRepository.findByType(type.name).map(MissionDocument::toDomain)

    override fun save(mission: Mission): Mono<Mission> =
        mongoRepository.save(MissionDocument.fromDomain(mission)).map(MissionDocument::toDomain)
}
