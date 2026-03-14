package com.study.webflux.rag.infrastructure.mission.document;

import java.time.Instant;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.CompoundIndexes;
import org.springframework.data.mongodb.core.mapping.Document;

import com.study.webflux.rag.domain.dialogue.model.UserId;
import com.study.webflux.rag.domain.mission.model.MissionId;
import com.study.webflux.rag.domain.mission.model.MissionStatus;
import com.study.webflux.rag.domain.mission.model.UserMission;

@Document(collection = "user_missions")
@CompoundIndexes({
	@CompoundIndex(name = "user_mission_idx", def = "{'userId': 1, 'missionId': 1}", unique = true)
})
public record UserMissionDocument(
	@Id String id,
	String userId,
	String missionId,
	String status,
	Instant completedAt,
	Instant rewardedAt
) {
	public static UserMissionDocument fromDomain(UserMission userMission) {
		String compositeId = userMission.userId().value() + ":" + userMission.missionId().value();
		return new UserMissionDocument(
			compositeId,
			userMission.userId().value(),
			userMission.missionId().value(),
			userMission.status().name(),
			userMission.completedAt(),
			userMission.rewardedAt());
	}

	public UserMission toDomain() {
		return new UserMission(
			UserId.of(userId),
			MissionId.of(missionId),
			MissionStatus.valueOf(status),
			completedAt,
			rewardedAt);
	}
}
