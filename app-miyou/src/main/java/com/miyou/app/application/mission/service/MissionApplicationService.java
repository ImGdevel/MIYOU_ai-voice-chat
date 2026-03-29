package com.miyou.app.application.mission.service;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.miyou.app.application.credit.usecase.CreditChargeUseCase;
import com.miyou.app.application.mission.usecase.MissionCompletionUseCase;
import com.miyou.app.application.mission.usecase.MissionQueryUseCase;
import com.miyou.app.domain.dialogue.model.UserId;
import com.miyou.app.domain.mission.model.Mission;
import com.miyou.app.domain.mission.model.MissionId;
import com.miyou.app.domain.mission.model.MissionStatus;
import com.miyou.app.domain.mission.model.UserMission;
import com.miyou.app.domain.mission.port.MissionRepository;
import com.miyou.app.domain.mission.port.UserMissionRepository;
import lombok.RequiredArgsConstructor;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Service
@RequiredArgsConstructor
public class MissionApplicationService implements MissionQueryUseCase, MissionCompletionUseCase {

	private final MissionRepository missionRepository;
	private final UserMissionRepository userMissionRepository;
	private final CreditChargeUseCase creditChargeUseCase;

	@Override
	public Flux<Mission> getAllMissions() {
		return missionRepository.findAll();
	}

	@Override
	public Flux<UserMission> getUserMissions(UserId userId) {
		return userMissionRepository.findByUserId(userId);
	}

	@Override
	@Transactional
	public Mono<UserMission> completeMission(UserId userId, MissionId missionId) {
		return missionRepository.findById(missionId)
			.switchIfEmpty(Mono.error(
				new ResponseStatusException(HttpStatus.NOT_FOUND, "미션을 찾을 수 없습니다: " + missionId.value())))
			.flatMap(mission -> userMissionRepository.findByUserIdAndMissionId(userId, missionId)
				.defaultIfEmpty(UserMission.start(userId, missionId))
				.flatMap(userMission -> validateAndComplete(userMission, mission, userId)));
	}

	private Mono<UserMission> validateAndComplete(UserMission userMission, Mission mission,
		UserId userId) {
		if (userMission.status() == MissionStatus.REWARDED && !mission.repeatable()) {
			return Mono.error(new ResponseStatusException(HttpStatus.CONFLICT,
				"이미 완료된 미션입니다: " + mission.missionId().value()));
		}
		UserMission completed = userMission.complete();
		UserMission rewarded = completed.reward();
		return userMissionRepository.save(rewarded)
			.flatMap(saved -> creditChargeUseCase
				.grantMissionReward(userId, mission.missionId(), mission.rewardAmount(),
					mission.type().name())
				.thenReturn(saved));
	}
}
