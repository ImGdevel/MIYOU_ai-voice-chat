package com.miyou.app.domain.credit.model;

import com.miyou.app.domain.mission.model.MissionId;

public record MissionReward(
	MissionId missionId,
	String missionType
) implements CreditSource {

	@Override
	public CreditSourceType sourceType() {
		return CreditSourceType.MISSION_REWARD;
	}
}
