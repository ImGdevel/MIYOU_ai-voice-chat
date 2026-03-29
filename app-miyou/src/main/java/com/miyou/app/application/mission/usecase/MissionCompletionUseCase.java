package com.miyou.app.application.mission.usecase;

import com.miyou.app.domain.dialogue.model.UserId;
import com.miyou.app.domain.mission.model.MissionId;
import com.miyou.app.domain.mission.model.UserMission;
import reactor.core.publisher.Mono;

public interface MissionCompletionUseCase {

	Mono<UserMission> completeMission(UserId userId, MissionId missionId);
}
