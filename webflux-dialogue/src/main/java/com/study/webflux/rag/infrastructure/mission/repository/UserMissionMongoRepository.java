package com.study.webflux.rag.infrastructure.mission.repository;

import org.springframework.data.mongodb.repository.ReactiveMongoRepository;

import com.study.webflux.rag.infrastructure.mission.document.UserMissionDocument;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public interface UserMissionMongoRepository
	extends ReactiveMongoRepository<UserMissionDocument, String> {

	Flux<UserMissionDocument> findByUserId(String userId);

	Mono<UserMissionDocument> findByUserIdAndMissionId(String userId, String missionId);
}
