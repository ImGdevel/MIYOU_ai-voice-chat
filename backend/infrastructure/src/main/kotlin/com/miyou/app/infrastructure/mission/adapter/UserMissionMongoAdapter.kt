package com.miyou.app.infrastructure.mission.adapter

import com.miyou.app.domain.mission.model.MissionId
import com.miyou.app.domain.mission.model.UserMission
import com.miyou.app.domain.mission.port.UserMissionRepository
import com.miyou.app.infrastructure.mission.document.UserMissionDocument
import com.miyou.app.infrastructure.mission.repository.UserMissionMongoRepository
import org.springframework.stereotype.Component
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono

@Component
class UserMissionMongoAdapter(
    private val mongoRepository: UserMissionMongoRepository,
) : UserMissionRepository {
    override fun findByUserId(userId: String): Flux<UserMission> =
        mongoRepository.findByUserId(userId).map(UserMissionDocument::toDomain)

    override fun findByUserIdAndMissionId(
        userId: String,
        missionId: MissionId,
    ): Mono<UserMission> =
        mongoRepository
            .findByUserIdAndMissionId(userId, missionId.value)
            .map(UserMissionDocument::toDomain)

    override fun save(userMission: UserMission): Mono<UserMission> =
        mongoRepository
            .save(UserMissionDocument.fromDomain(userMission))
            .map(UserMissionDocument::toDomain)
}
