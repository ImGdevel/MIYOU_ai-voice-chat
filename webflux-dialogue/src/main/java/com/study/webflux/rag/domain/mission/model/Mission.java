package com.study.webflux.rag.domain.mission.model;

public record Mission(
	MissionId missionId,
	MissionType type,
	String name,
	String description,
	long rewardAmount,
	boolean repeatable
) {
	public Mission {
		if (missionId == null) {
			throw new IllegalArgumentException("missionId cannot be null");
		}
		if (type == null) {
			throw new IllegalArgumentException("missionType cannot be null");
		}
		if (name == null || name.isBlank()) {
			throw new IllegalArgumentException("mission name cannot be blank");
		}
		if (rewardAmount <= 0) {
			throw new IllegalArgumentException("rewardAmount must be positive");
		}
	}

	public static Mission create(MissionType type, String name, String description, long rewardAmount,
		boolean repeatable) {
		return new Mission(MissionId.generate(), type, name, description, rewardAmount, repeatable);
	}
}
