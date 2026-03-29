package com.miyou.app.domain.mission.port;

import com.miyou.app.domain.mission.model.Mission;
import com.miyou.app.domain.mission.model.MissionId;
import com.miyou.app.domain.mission.model.MissionType;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public interface MissionRepository {

	Mono<Mission> findById(MissionId missionId);

	Flux<Mission> findAll();

	Flux<Mission> findByType(MissionType type);

	Mono<Mission> save(Mission mission);
}
