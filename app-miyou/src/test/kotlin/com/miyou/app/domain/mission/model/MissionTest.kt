package com.miyou.app.domain.mission.model

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

@DisplayName("Mission 도메인 모델")
class MissionTest {
    @Nested
    @DisplayName("create() 팩토리")
    inner class Create {
        @Test
        @DisplayName("정상 생성하면 missionId가 자동 생성된다")
        fun create_generatesUniqueId() {
            val mission1 = Mission.create(MissionType.SHARE_SERVICE, "공유 미션", "설명", 500L, false)
            val mission2 = Mission.create(MissionType.SHARE_SERVICE, "공유 미션", "설명", 500L, false)

            assertThat(mission1.missionId().value()).isNotEqualTo(mission2.missionId().value())
        }

        @Test
        @DisplayName("rewardAmount가 0 이하이면 예외가 발생한다")
        fun create_nonPositiveReward_throws() {
            assertThatThrownBy {
                Mission.create(MissionType.TASK_COMPLETION, "테스트 미션", "설명", 0L, false)
            }.isInstanceOf(IllegalArgumentException::class.java)
                .hasMessageContaining("rewardAmount must be positive")
        }

        @Test
        @DisplayName("name이 blank이면 예외가 발생한다")
        fun create_blankName_throws() {
            assertThatThrownBy {
                Mission.create(MissionType.REFERRAL, "  ", "설명", 100L, false)
            }.isInstanceOf(IllegalArgumentException::class.java)
                .hasMessageContaining("mission name cannot be blank")
        }

        @Test
        @DisplayName("repeatable=true로 생성하면 반복 가능 상태가 저장된다")
        fun create_repeatable_setsFlag() {
            val mission = Mission.create(MissionType.CUSTOM, "반복 미션", "설명", 100L, true)

            assertThat(mission.repeatable()).isTrue()
        }
    }
}
