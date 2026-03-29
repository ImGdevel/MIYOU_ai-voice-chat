package com.miyou.app.domain.mission.model;

import java.time.Instant;

import com.miyou.app.domain.dialogue.model.UserId;

public record UserMission(
	UserId userId,
	MissionId missionId,
	MissionStatus status,
	Instant completedAt,
	Instant rewardedAt
) {
	public UserMission {
		if (userId == null) {
			throw new IllegalArgumentException("userId cannot be null");
		}
		if (missionId == null) {
			throw new IllegalArgumentException("missionId cannot be null");
		}
		if (status == null) {
			throw new IllegalArgumentException("status cannot be null");
		}
	}

	public static UserMission start(UserId userId, MissionId missionId) {
		return new UserMission(userId, missionId, MissionStatus.AVAILABLE, null, null);
	}

	public UserMission complete() {
		return new UserMission(userId, missionId, MissionStatus.COMPLETED, Instant.now(), null);
	}

	public UserMission reward() {
		return new UserMission(userId, missionId, MissionStatus.REWARDED, completedAt,
			Instant.now());
	}
}
