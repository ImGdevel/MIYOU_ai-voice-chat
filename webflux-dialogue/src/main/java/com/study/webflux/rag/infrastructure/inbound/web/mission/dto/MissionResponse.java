package com.study.webflux.rag.infrastructure.inbound.web.mission.dto;

import com.study.webflux.rag.domain.mission.model.Mission;

public record MissionResponse(
	String missionId,
	String type,
	String name,
	String description,
	long rewardAmount,
	boolean repeatable
) {
	public static MissionResponse from(Mission mission) {
		return new MissionResponse(
			mission.missionId().value(),
			mission.type().name(),
			mission.name(),
			mission.description(),
			mission.rewardAmount(),
			mission.repeatable());
	}
}
