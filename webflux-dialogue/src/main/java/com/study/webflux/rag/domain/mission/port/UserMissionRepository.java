package com.study.webflux.rag.domain.mission.port;

import com.study.webflux.rag.domain.dialogue.model.UserId;
import com.study.webflux.rag.domain.mission.model.MissionId;
import com.study.webflux.rag.domain.mission.model.UserMission;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public interface UserMissionRepository {

	Flux<UserMission> findByUserId(UserId userId);

	Mono<UserMission> findByUserIdAndMissionId(UserId userId, MissionId missionId);

	Mono<UserMission> save(UserMission userMission);
}
