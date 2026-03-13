package com.study.webflux.rag.domain.credit.model.source;

import com.study.webflux.rag.domain.credit.model.CreditSource;
import com.study.webflux.rag.domain.credit.model.CreditSourceType;
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
