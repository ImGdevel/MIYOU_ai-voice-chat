package com.study.webflux.rag.infrastructure.mission.repository;

import org.springframework.data.mongodb.repository.ReactiveMongoRepository;

import com.study.webflux.rag.infrastructure.mission.document.MissionDocument;
import reactor.core.publisher.Flux;

public interface MissionMongoRepository
	extends ReactiveMongoRepository<MissionDocument, String> {

	Flux<MissionDocument> findByType(String type);
}
