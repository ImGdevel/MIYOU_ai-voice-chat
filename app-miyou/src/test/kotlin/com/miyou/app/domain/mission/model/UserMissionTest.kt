package com.miyou.app.domain.mission.model

import com.miyou.app.fixture.MissionFixture
import com.miyou.app.fixture.UserIdFixture
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.time.Instant

@DisplayName("UserMission 상태 전이")
class UserMissionTest {
    @Nested
    @DisplayName("start()")
    inner class Start {
        @Test
        @DisplayName("start()는 AVAILABLE 상태로 시작하고 완료/보상 시간은 비어 있다")
        fun start_isAvailable() {
            val userMission =
                UserMission.start(
                    UserIdFixture.create(),
                    MissionId.of(MissionFixture.DEFAULT_MISSION_ID),
                )

            assertThat(userMission.status()).isEqualTo(MissionStatus.AVAILABLE)
            assertThat(userMission.completedAt()).isNull()
            assertThat(userMission.rewardedAt()).isNull()
        }
    }

    @Nested
    @DisplayName("complete()")
    inner class Complete {
        @Test
        @DisplayName("AVAILABLE 상태에서 complete()를 호출하면 COMPLETED가 되고 완료 시간이 기록된다")
        fun complete_setsCompletedStatus() {
            val before = Instant.now()
            val available =
                UserMission.start(
                    UserIdFixture.create(),
                    MissionId.of(MissionFixture.DEFAULT_MISSION_ID),
                )

            val completed = available.complete()

            assertThat(completed.status()).isEqualTo(MissionStatus.COMPLETED)
            assertThat(completed.completedAt()).isNotNull()
            assertThat(completed.completedAt()).isAfterOrEqualTo(before)
            assertThat(completed.rewardedAt()).isNull()
        }

        @Test
        @DisplayName("complete()는 원본 AVAILABLE 상태를 변경하지 않는다")
        fun complete_isImmutable() {
            val original =
                UserMission.start(
                    UserIdFixture.create(),
                    MissionId.of(MissionFixture.DEFAULT_MISSION_ID),
                )

            original.complete()

            assertThat(original.status()).isEqualTo(MissionStatus.AVAILABLE)
        }
    }

    @Nested
    @DisplayName("reward()")
    inner class Reward {
        @Test
        @DisplayName("COMPLETED 상태에서 reward()를 호출하면 REWARDED가 되고 보상 시간이 기록된다")
        fun reward_setsRewardedStatus() {
            val completedAt = Instant.now().minusSeconds(10)
            val completed =
                UserMission(
                    UserIdFixture.create(),
                    MissionId.of(MissionFixture.DEFAULT_MISSION_ID),
                    MissionStatus.COMPLETED,
                    completedAt,
                    null,
                )

            val before = Instant.now()
            val rewarded = completed.reward()

            assertThat(rewarded.status()).isEqualTo(MissionStatus.REWARDED)
            assertThat(rewarded.rewardedAt()).isNotNull()
            assertThat(rewarded.rewardedAt()).isAfterOrEqualTo(before)
            assertThat(rewarded.completedAt()).isEqualTo(completedAt)
        }

        @Test
        @DisplayName("AVAILABLE에서 complete() 후 reward()를 호출하면 전체 상태 전이가 완료된다")
        fun fullTransition_availableToRewarded() {
            val userMission =
                UserMission.start(
                    UserIdFixture.create(),
                    MissionId.of(MissionFixture.DEFAULT_MISSION_ID),
                )

            val rewarded = userMission.complete().reward()

            assertThat(rewarded.status()).isEqualTo(MissionStatus.REWARDED)
            assertThat(rewarded.completedAt()).isNotNull()
            assertThat(rewarded.rewardedAt()).isNotNull()
            assertThat(rewarded.rewardedAt()).isAfterOrEqualTo(rewarded.completedAt())
        }
    }
}
