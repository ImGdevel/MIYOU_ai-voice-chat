package com.miyou.app.fixture

import com.miyou.app.domain.dialogue.model.UserId
import com.miyou.app.domain.mission.model.Mission
import com.miyou.app.domain.mission.model.MissionId
import com.miyou.app.domain.mission.model.MissionStatus
import com.miyou.app.domain.mission.model.MissionType
import com.miyou.app.domain.mission.model.UserMission
import java.time.Instant

object MissionFixture {

	const val DEFAULT_MISSION_ID = "mission-share-1"
	const val DEFAULT_REWARD = 500L

	@JvmStatic
	fun create(): Mission =
		Mission(
			MissionId.of(DEFAULT_MISSION_ID),
			MissionType.SHARE_SERVICE,
			"서비스 공유 미션",
			"서비스를 공유하고 크레딧을 받으세요",
			DEFAULT_REWARD,
			false,
		)

	@JvmStatic
	fun create(missionId: String, type: MissionType, reward: Long, repeatable: Boolean): Mission =
		Mission(
			MissionId.of(missionId),
			type,
			"${type.name} 미션",
			"${type.name} 설명",
			reward,
			repeatable,
		)

	@JvmStatic
	fun userMission(userId: UserId): UserMission = UserMission.start(userId, MissionId.of(DEFAULT_MISSION_ID))

	@JvmStatic
	fun completedUserMission(userId: UserId): UserMission =
		UserMission(userId, MissionId.of(DEFAULT_MISSION_ID), MissionStatus.REWARDED, Instant.now(), Instant.now())
}
