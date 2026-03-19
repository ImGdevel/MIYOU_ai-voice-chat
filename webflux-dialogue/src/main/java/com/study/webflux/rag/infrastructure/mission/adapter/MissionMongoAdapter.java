package com.study.webflux.rag.infrastructure.mission.adapter;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Component;

import com.study.webflux.rag.domain.mission.model.Mission;
import com.study.webflux.rag.domain.mission.model.MissionId;
import com.study.webflux.rag.domain.mission.model.MissionType;
import com.study.webflux.rag.domain.mission.port.MissionRepository;
import com.study.webflux.rag.infrastructure.mission.document.MissionDocument;
import com.study.webflux.rag.infrastructure.mission.repository.MissionMongoRepository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Component
@RequiredArgsConstructor
public class MissionMongoAdapter implements MissionRepository {

	private final MissionMongoRepository mongoRepository;

	@Override
	public Mono<Mission> findById(MissionId missionId) {
		return mongoRepository.findById(missionId.value()).map(MissionDocument::toDomain);
	}

	@Override
	public Flux<Mission> findAll() {
		return mongoRepository.findAll().map(MissionDocument::toDomain);
	}

	@Override
	public Flux<Mission> findByType(MissionType type) {
		return mongoRepository.findByType(type.name()).map(MissionDocument::toDomain);
	}

	@Override
	public Mono<Mission> save(Mission mission) {
		return mongoRepository.save(MissionDocument.fromDomain(mission))
			.map(MissionDocument::toDomain);
	}
}
