package com.study.webflux.rag.domain.credit.model;

import com.study.webflux.rag.domain.mission.model.MissionId;

public record MissionReward(
	MissionId missionId,
	String missionType
) implements CreditSource {

	@Override
	public CreditSourceType sourceType() {
		return CreditSourceType.MISSION_REWARD;
	}
}
