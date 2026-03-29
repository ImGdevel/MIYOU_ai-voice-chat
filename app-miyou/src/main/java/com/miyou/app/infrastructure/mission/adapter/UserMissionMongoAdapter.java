package com.miyou.app.infrastructure.mission.adapter;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Component;

import com.miyou.app.domain.dialogue.model.UserId;
import com.miyou.app.domain.mission.model.MissionId;
import com.miyou.app.domain.mission.model.UserMission;
import com.miyou.app.domain.mission.port.UserMissionRepository;
import com.miyou.app.infrastructure.mission.document.UserMissionDocument;
import com.miyou.app.infrastructure.mission.repository.UserMissionMongoRepository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Component
@RequiredArgsConstructor
public class UserMissionMongoAdapter implements UserMissionRepository {

	private final UserMissionMongoRepository mongoRepository;

	@Override
	public Flux<UserMission> findByUserId(UserId userId) {
		return mongoRepository.findByUserId(userId.value()).map(UserMissionDocument::toDomain);
	}

	@Override
	public Mono<UserMission> findByUserIdAndMissionId(UserId userId, MissionId missionId) {
		return mongoRepository.findByUserIdAndMissionId(userId.value(), missionId.value())
			.map(UserMissionDocument::toDomain);
	}

	@Override
	public Mono<UserMission> save(UserMission userMission) {
		return mongoRepository.save(UserMissionDocument.fromDomain(userMission))
			.map(UserMissionDocument::toDomain);
	}
}
