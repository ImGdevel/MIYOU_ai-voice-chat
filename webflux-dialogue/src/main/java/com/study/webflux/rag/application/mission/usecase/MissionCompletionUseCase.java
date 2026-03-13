package com.study.webflux.rag.application.mission.usecase;

import com.study.webflux.rag.domain.dialogue.model.UserId;
import com.study.webflux.rag.domain.mission.model.MissionId;
import com.study.webflux.rag.domain.mission.model.UserMission;
import reactor.core.publisher.Mono;

public interface MissionCompletionUseCase {

	Mono<UserMission> completeMission(UserId userId, MissionId missionId);
}
