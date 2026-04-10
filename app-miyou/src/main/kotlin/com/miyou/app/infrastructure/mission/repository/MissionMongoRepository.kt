package com.miyou.app.infrastructure.mission.repository

import com.miyou.app.infrastructure.mission.document.MissionDocument
import org.springframework.data.mongodb.repository.ReactiveMongoRepository
import reactor.core.publisher.Flux

interface MissionMongoRepository : ReactiveMongoRepository<MissionDocument, String> {
    fun findByType(type: String): Flux<MissionDocument>
}
