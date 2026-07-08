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
            // given & when - 사용자가 미션을 시작하는 상황
            val userMission =
                UserMission.start(
                    UserIdFixture.create(),
                    MissionId.of(MissionFixture.DEFAULT_MISSION_ID),
                )

            // then - 초기 상태는 AVAILABLE이고 시간 관련 필드들은 null인지 검증
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
            // given - 시작 전 시간 기준 및 시작한 미션 준비
            val before = Instant.now()
            val available =
                UserMission.start(
                    UserIdFixture.create(),
                    MissionId.of(MissionFixture.DEFAULT_MISSION_ID),
                )

            // when - 미션 완료 처리
            val completed = available.complete()

            // then - 미션 상태가 COMPLETED로 전이되고 완료 일시가 설정되었는지 검증
            assertThat(completed.status()).isEqualTo(MissionStatus.COMPLETED)
            assertThat(completed.completedAt()).isNotNull()
            assertThat(completed.completedAt()).isAfterOrEqualTo(before)
            assertThat(completed.rewardedAt()).isNull()
        }

        @Test
        @DisplayName("complete()는 원본 AVAILABLE 상태를 변경하지 않는다")
        fun complete_isImmutable() {
            // given - 시작한 미션 준비 (불변 객체 검증 목적)
            val original =
                UserMission.start(
                    UserIdFixture.create(),
                    MissionId.of(MissionFixture.DEFAULT_MISSION_ID),
                )

            // when - 미션을 완료처리 (단, 새로운 객체 반환)
            original.complete()

            // then - 원본 객체 상태는 변하지 않고 AVAILABLE을 유지하는지 검증 (불변성 확인)
            assertThat(original.status()).isEqualTo(MissionStatus.AVAILABLE)
        }
    }

    @Nested
    @DisplayName("reward()")
    inner class Reward {
        @Test
        @DisplayName("COMPLETED 상태에서 reward()를 호출하면 REWARDED가 되고 보상 시간이 기록된다")
        fun reward_setsRewardedStatus() {
            // given - 완료된 미션 상태 준비
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
            // when - 보상 처리 실행
            val rewarded = completed.reward()

            // then - 미션 상태가 REWARDED로 전이되고 보상 시각이 기록되었는지 검증
            assertThat(rewarded.status()).isEqualTo(MissionStatus.REWARDED)
            assertThat(rewarded.rewardedAt()).isNotNull()
            assertThat(rewarded.rewardedAt()).isAfterOrEqualTo(before)
            assertThat(rewarded.completedAt()).isEqualTo(completedAt)
        }

        @Test
        @DisplayName("AVAILABLE에서 complete() 후 reward()를 호출하면 전체 상태 전이가 완료된다")
        fun fullTransition_availableToRewarded() {
            // given - 미션 시작 상태 준비
            val userMission =
                UserMission.start(
                    UserIdFixture.create(),
                    MissionId.of(MissionFixture.DEFAULT_MISSION_ID),
                )

            // when - 완료 후 보상까지 연쇄 처리 실행
            val rewarded = userMission.complete().reward()

            // then - 미션이 최종 완료 및 보상 처리 완료 상태(REWARDED)가 되었는지 최종 검증
            assertThat(rewarded.status()).isEqualTo(MissionStatus.REWARDED)
            assertThat(rewarded.completedAt()).isNotNull()
            assertThat(rewarded.rewardedAt()).isNotNull()
            assertThat(rewarded.rewardedAt()).isAfterOrEqualTo(rewarded.completedAt())
        }
    }
}
