package com.miyou.app.fixture;

import com.miyou.app.domain.mission.model.Mission;
import com.miyou.app.domain.mission.model.MissionId;
import com.miyou.app.domain.mission.model.MissionStatus;
import com.miyou.app.domain.mission.model.MissionType;
import com.miyou.app.domain.mission.model.UserMission;

public final class MissionFixture {

	public static final String DEFAULT_MISSION_ID = "mission-share-1";
	public static final long DEFAULT_REWARD = 500L;

	private MissionFixture() {
	}

	public static Mission create() {
		return new Mission(
			MissionId.of(DEFAULT_MISSION_ID),
			MissionType.SHARE_SERVICE,
			"서비스 공유 미션",
			"서비스를 공유하고 크레딧을 받으세요",
			DEFAULT_REWARD,
			false);
	}

	public static Mission create(String missionId,
		MissionType type,
		long reward,
		boolean repeatable) {
		return new Mission(
			MissionId.of(missionId),
			type,
			type.name() + " 미션",
			type.name() + " 설명",
			reward,
			repeatable);
	}

	public static UserMission userMission(com.miyou.app.domain.dialogue.model.UserId userId) {
		return UserMission.start(userId, MissionId.of(DEFAULT_MISSION_ID));
	}

	public static UserMission completedUserMission(
		com.miyou.app.domain.dialogue.model.UserId userId) {
		return new UserMission(userId, MissionId.of(DEFAULT_MISSION_ID), MissionStatus.REWARDED,
			java.time.Instant.now(), java.time.Instant.now());
	}
}
