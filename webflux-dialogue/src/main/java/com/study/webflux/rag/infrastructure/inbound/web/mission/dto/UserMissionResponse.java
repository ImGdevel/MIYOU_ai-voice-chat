package com.study.webflux.rag.infrastructure.inbound.web.mission.dto;

import java.time.Instant;

import com.study.webflux.rag.domain.mission.model.UserMission;

public record UserMissionResponse(
	String userId,
	String missionId,
	String status,
	Instant completedAt,
	Instant rewardedAt
) {
	public static UserMissionResponse from(UserMission userMission) {
		return new UserMissionResponse(
			userMission.userId().value(),
			userMission.missionId().value(),
			userMission.status().name(),
			userMission.completedAt(),
			userMission.rewardedAt());
	}
}
