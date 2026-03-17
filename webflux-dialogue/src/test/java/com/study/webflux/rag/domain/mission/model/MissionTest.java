package com.study.webflux.rag.domain.mission.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("Mission 도메인 모델")
class MissionTest {

	@Nested
	@DisplayName("create() 팩토리")
	class Create {

		@Test
		@DisplayName("정상 생성 시 UUID missionId가 자동 할당된다")
		void create_generatesUniqueId() {
			Mission m1 = Mission.create(MissionType.SHARE_SERVICE, "공유 미션", "설명", 500L, false);
			Mission m2 = Mission.create(MissionType.SHARE_SERVICE, "공유 미션", "설명", 500L, false);

			assertThat(m1.missionId().value()).isNotEqualTo(m2.missionId().value());
		}

		@Test
		@DisplayName("rewardAmount가 0 이하이면 예외 발생")
		void create_nonPositiveReward_throws() {
			assertThatThrownBy(() ->
				Mission.create(MissionType.TASK_COMPLETION, "태스크 미션", "설명", 0L, false))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessageContaining("rewardAmount must be positive");
		}

		@Test
		@DisplayName("name이 blank이면 예외 발생")
		void create_blankName_throws() {
			assertThatThrownBy(() ->
				Mission.create(MissionType.REFERRAL, "  ", "설명", 100L, false))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessageContaining("mission name cannot be blank");
		}

		@Test
		@DisplayName("repeatable=true인 미션은 반복 가능 플래그가 설정된다")
		void create_repeatable_setsFlag() {
			Mission mission = Mission.create(MissionType.CUSTOM, "반복 미션", "설명", 100L, true);

			assertThat(mission.repeatable()).isTrue();
		}
	}
}
