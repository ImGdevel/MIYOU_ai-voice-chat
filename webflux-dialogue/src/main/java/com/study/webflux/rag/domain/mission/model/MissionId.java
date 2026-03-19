package com.study.webflux.rag.domain.mission.model;

import java.util.UUID;

public record MissionId(
	String value
) {
	public MissionId {
		if (value == null || value.isBlank()) {
			throw new IllegalArgumentException("missionId cannot be null or blank");
		}
	}

	public static MissionId of(String value) {
		return new MissionId(value);
	}

	public static MissionId generate() {
		return new MissionId(UUID.randomUUID().toString());
	}
}
