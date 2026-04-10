package com.miyou.app.infrastructure.mission.repository

import com.miyou.app.infrastructure.mission.document.UserMissionDocument
import org.springframework.data.mongodb.repository.ReactiveMongoRepository
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono

interface UserMissionMongoRepository : ReactiveMongoRepository<UserMissionDocument, String> {
    fun findByUserId(userId: String): Flux<UserMissionDocument>

    fun findByUserIdAndMissionId(
        userId: String,
        missionId: String,
    ): Mono<UserMissionDocument>
}
