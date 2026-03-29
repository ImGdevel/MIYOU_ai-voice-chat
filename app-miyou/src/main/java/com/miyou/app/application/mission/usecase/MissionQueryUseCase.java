package com.miyou.app.application.mission.usecase;

import com.miyou.app.domain.dialogue.model.UserId;
import com.miyou.app.domain.mission.model.Mission;
import com.miyou.app.domain.mission.model.UserMission;
import reactor.core.publisher.Flux;

public interface MissionQueryUseCase {

	Flux<Mission> getAllMissions();

	Flux<UserMission> getUserMissions(UserId userId);
}
