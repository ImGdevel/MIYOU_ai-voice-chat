package com.study.webflux.rag.application.mission.usecase;

import com.study.webflux.rag.domain.dialogue.model.UserId;
import com.study.webflux.rag.domain.mission.model.Mission;
import com.study.webflux.rag.domain.mission.model.UserMission;
import reactor.core.publisher.Flux;

public interface MissionQueryUseCase {

	Flux<Mission> getAllMissions();

	Flux<UserMission> getUserMissions(UserId userId);
}
