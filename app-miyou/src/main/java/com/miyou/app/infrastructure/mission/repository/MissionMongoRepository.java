package com.miyou.app.infrastructure.mission.repository;

import org.springframework.data.mongodb.repository.ReactiveMongoRepository;

import com.miyou.app.infrastructure.mission.document.MissionDocument;
import reactor.core.publisher.Flux;

public interface MissionMongoRepository
	extends ReactiveMongoRepository<MissionDocument, String> {

	Flux<MissionDocument> findByType(String type);
}
