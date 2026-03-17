package com.study.webflux.rag.domain.mission.model;

import java.time.Instant;

import com.study.webflux.rag.fixture.MissionFixture;
import com.study.webflux.rag.fixture.UserIdFixture;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("UserMission 상태 전이")
class UserMissionTest {

	@Nested
	@DisplayName("start()")
	class Start {

		@Test
		@DisplayName("start() 시 AVAILABLE 상태, completedAt/rewardedAt은 null")
		void start_isAvailable() {
			UserMission userMission = UserMission.start(
				UserIdFixture.create(), MissionId.of(MissionFixture.DEFAULT_MISSION_ID));

			assertThat(userMission.status()).isEqualTo(MissionStatus.AVAILABLE);
			assertThat(userMission.completedAt()).isNull();
			assertThat(userMission.rewardedAt()).isNull();
		}
	}

	@Nested
	@DisplayName("complete()")
	class Complete {

		@Test
		@DisplayName("AVAILABLE → complete() → COMPLETED, completedAt 기록")
		void complete_setsCompletedStatus() {
			Instant before = Instant.now();
			UserMission available = UserMission.start(
				UserIdFixture.create(), MissionId.of(MissionFixture.DEFAULT_MISSION_ID));

			UserMission completed = available.complete();

			assertThat(completed.status()).isEqualTo(MissionStatus.COMPLETED);
			assertThat(completed.completedAt()).isNotNull();
			assertThat(completed.completedAt()).isAfterOrEqualTo(before);
			assertThat(completed.rewardedAt()).isNull();
		}

		@Test
		@DisplayName("complete()는 불변 — 원본 AVAILABLE 상태 유지")
		void complete_isImmutable() {
			UserMission original = UserMission.start(
				UserIdFixture.create(), MissionId.of(MissionFixture.DEFAULT_MISSION_ID));

			original.complete();

			assertThat(original.status()).isEqualTo(MissionStatus.AVAILABLE);
		}
	}

	@Nested
	@DisplayName("reward()")
	class Reward {

		@Test
		@DisplayName("COMPLETED → reward() → REWARDED, rewardedAt 기록, completedAt 유지")
		void reward_setsRewardedStatus() {
			Instant completedAt = Instant.now().minusSeconds(10);
			UserMission completed = new UserMission(
				UserIdFixture.create(),
				MissionId.of(MissionFixture.DEFAULT_MISSION_ID),
				MissionStatus.COMPLETED,
				completedAt,
				null);

			Instant before = Instant.now();
			UserMission rewarded = completed.reward();

			assertThat(rewarded.status()).isEqualTo(MissionStatus.REWARDED);
			assertThat(rewarded.rewardedAt()).isNotNull();
			assertThat(rewarded.rewardedAt()).isAfterOrEqualTo(before);
			assertThat(rewarded.completedAt()).isEqualTo(completedAt);
		}

		@Test
		@DisplayName("AVAILABLE → complete() → reward() 전체 상태 전이 검증")
		void fullTransition_availableToRewarded() {
			UserMission userMission = UserMission.start(
				UserIdFixture.create(), MissionId.of(MissionFixture.DEFAULT_MISSION_ID));

			UserMission rewarded = userMission.complete().reward();

			assertThat(rewarded.status()).isEqualTo(MissionStatus.REWARDED);
			assertThat(rewarded.completedAt()).isNotNull();
			assertThat(rewarded.rewardedAt()).isNotNull();
			assertThat(rewarded.rewardedAt()).isAfterOrEqualTo(rewarded.completedAt());
		}
	}
}
