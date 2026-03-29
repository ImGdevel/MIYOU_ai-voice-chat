package com.miyou.app.infrastructure.mission.document;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import com.miyou.app.domain.mission.model.Mission;
import com.miyou.app.domain.mission.model.MissionId;
import com.miyou.app.domain.mission.model.MissionType;

@Document(collection = "missions")
public record MissionDocument(
	@Id String id,
	@Indexed String type,
	String name,
	String description,
	long rewardAmount,
	boolean repeatable
) {
	public static MissionDocument fromDomain(Mission mission) {
		return new MissionDocument(
			mission.missionId().value(),
			mission.type().name(),
			mission.name(),
			mission.description(),
			mission.rewardAmount(),
			mission.repeatable());
	}

	public Mission toDomain() {
		return new Mission(
			MissionId.of(id),
			MissionType.valueOf(type),
			name,
			description,
			rewardAmount,
			repeatable);
	}
}
