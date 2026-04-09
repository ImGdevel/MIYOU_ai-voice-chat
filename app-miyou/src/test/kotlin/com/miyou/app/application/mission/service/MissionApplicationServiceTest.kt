package com.miyou.app.application.mission.service

import com.miyou.app.application.credit.usecase.CreditChargeUseCase
import com.miyou.app.fixture.CreditTransactionFixture
import com.miyou.app.fixture.MissionFixture
import com.miyou.app.fixture.UserIdFixture
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.ArgumentMatchers.any
import org.mockito.ArgumentMatchers.anyLong
import org.mockito.ArgumentMatchers.anyString
import org.mockito.ArgumentMatchers.eq
import org.mockito.Mock
import org.mockito.Mockito.never
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
import org.mockito.invocation.InvocationOnMock
import org.mockito.junit.jupiter.MockitoExtension
import org.springframework.http.HttpStatus
import org.springframework.web.server.ResponseStatusException
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import reactor.test.StepVerifier
import java.time.Instant

@ExtendWith(MockitoExtension::class)
@DisplayName("MissionApplicationService")
class MissionApplicationServiceTest {
    @Mock
    private lateinit var missionRepository: com.miyou.app.domain.mission.port.MissionRepository

    @Mock
    private lateinit var userMissionRepository: com.miyou.app.domain.mission.port.UserMissionRepository

    @Mock
    private lateinit var creditChargeUseCase: CreditChargeUseCase

    private lateinit var service: MissionApplicationService

    @BeforeEach
    fun setUp() {
        service = MissionApplicationService(missionRepository, userMissionRepository, creditChargeUseCase)
    }

    @Nested
    @DisplayName("getAllMissions()")
    inner class GetAllMissions {
        @Test
        @DisplayName("전체 미션 목록을 반환한다")
        fun getAllMissions_returnsAll() {
            val mission1 = MissionFixture.create()
            val mission2 =
                MissionFixture.create(
                    "referral-1",
                    com.miyou.app.domain.mission.model.MissionType.REFERRAL,
                    300L,
                    false
                )
            `when`(missionRepository.findAll()).thenReturn(Flux.just(mission1, mission2))

            StepVerifier
                .create(service.getAllMissions())
                .expectNextCount(2)
                .verifyComplete()
        }

        @Test
        @DisplayName("미션이 없으면 빈 Flux를 반환한다")
        fun getAllMissions_empty_returnsEmpty() {
            `when`(missionRepository.findAll()).thenReturn(Flux.empty())

            StepVerifier
                .create(service.getAllMissions())
                .verifyComplete()
        }
    }

    @Nested
    @DisplayName("getUserMissions()")
    inner class GetUserMissions {
        @Test
        @DisplayName("유저의 진행 중인 미션 목록을 반환한다")
        fun getUserMissions_returnsUserMissions() {
            val userId = UserIdFixture.create()
            val userMission = MissionFixture.userMission(userId)
            `when`(userMissionRepository.findByUserId(userId)).thenReturn(Flux.just(userMission))

            StepVerifier
                .create(service.getUserMissions(userId))
                .assertNext { result -> assertThat(result.userId()).isEqualTo(userId) }
                .verifyComplete()
        }
    }

    @Nested
    @DisplayName("completeMission()")
    inner class CompleteMission {
        @Test
        @DisplayName("AVAILABLE 상태 유저가 미션 완료 시 REWARDED 상태가 되고 크레딧이 지급된다")
        fun completeMission_success_grantsRewardAndRewardedStatus() {
            val userId = UserIdFixture.create()
            val mission = MissionFixture.create()
            val missionId = mission.missionId()
            val rewardTx = CreditTransactionFixture.signupBonus(userId, 500L)

            `when`(missionRepository.findById(missionId)).thenReturn(Mono.just(mission))
            `when`(userMissionRepository.findByUserIdAndMissionId(userId, missionId)).thenReturn(Mono.empty())
            `when`(userMissionRepository.save(any())).thenAnswer { invocation: InvocationOnMock ->
                Mono.just(invocation.getArgument<com.miyou.app.domain.mission.model.UserMission>(0))
            }
            `when`(
                creditChargeUseCase.grantMissionReward(eq(userId), eq(missionId), anyLong(), anyString()),
            ).thenReturn(Mono.just(rewardTx))

            StepVerifier
                .create(service.completeMission(userId, missionId))
                .assertNext { result ->
                    assertThat(result.userId()).isEqualTo(userId)
                    assertThat(result.missionId()).isEqualTo(missionId)
                    assertThat(result.status()).isEqualTo(com.miyou.app.domain.mission.model.MissionStatus.REWARDED)
                    assertThat(result.completedAt()).isNotNull()
                    assertThat(result.rewardedAt()).isNotNull()
                }.verifyComplete()

            verify(creditChargeUseCase).grantMissionReward(
                eq(userId),
                eq(missionId),
                eq(MissionFixture.DEFAULT_REWARD),
                eq("SHARE_SERVICE"),
            )
        }

        @Test
        @DisplayName("존재하지 않는 미션 ID는 404 반환")
        fun completeMission_notFound_throws404() {
            val userId = UserIdFixture.create()
            val unknownId =
                com.miyou.app.domain.mission.model.MissionId
                    .of("unknown-mission")
            `when`(missionRepository.findById(unknownId)).thenReturn(Mono.empty())

            StepVerifier
                .create(service.completeMission(userId, unknownId))
                .expectErrorMatches { error ->
                    error is ResponseStatusException && error.statusCode == HttpStatus.NOT_FOUND
                }.verify()

            verify(creditChargeUseCase, never()).grantMissionReward(any(), any(), anyLong(), anyString())
        }

        @Test
        @DisplayName("이미 REWARDED 상태의 비반복 미션은 409 Conflict 반환")
        fun completeMission_alreadyRewarded_nonRepeatable_throws409() {
            val userId = UserIdFixture.create()
            val mission =
                MissionFixture.create(
                    "one-time-mission",
                    com.miyou.app.domain.mission.model.MissionType.SHARE_SERVICE,
                    500L,
                    false
                )
            val missionId = mission.missionId()
            val completedWithSameId =
                com.miyou.app.domain.mission.model.UserMission(
                    userId,
                    missionId,
                    com.miyou.app.domain.mission.model.MissionStatus.REWARDED,
                    Instant.now(),
                    Instant.now(),
                )

            `when`(missionRepository.findById(missionId)).thenReturn(Mono.just(mission))
            `when`(userMissionRepository.findByUserIdAndMissionId(userId, missionId))
                .thenReturn(Mono.just(completedWithSameId))

            StepVerifier
                .create(service.completeMission(userId, missionId))
                .expectErrorMatches { error ->
                    error is ResponseStatusException && error.statusCode == HttpStatus.CONFLICT
                }.verify()

            verify(creditChargeUseCase, never()).grantMissionReward(any(), any(), anyLong(), anyString())
        }

        @Test
        @DisplayName("반복 가능 미션은 이미 REWARDED 상태여도 다시 완료 가능하다")
        fun completeMission_repeatable_alreadyRewarded_succeeds() {
            val userId = UserIdFixture.create()
            val repeatableMission =
                MissionFixture.create(
                    "daily-mission",
                    com.miyou.app.domain.mission.model.MissionType.TASK_COMPLETION,
                    100L,
                    true,
                )
            val missionId = repeatableMission.missionId()
            val previouslyRewarded =
                com.miyou.app.domain.mission.model.UserMission(
                    userId,
                    missionId,
                    com.miyou.app.domain.mission.model.MissionStatus.REWARDED,
                    Instant.now().minusSeconds(100),
                    Instant.now().minusSeconds(90),
                )
            val rewardTx = CreditTransactionFixture.signupBonus(userId, 100L)

            `when`(missionRepository.findById(missionId)).thenReturn(Mono.just(repeatableMission))
            `when`(userMissionRepository.findByUserIdAndMissionId(userId, missionId))
                .thenReturn(Mono.just(previouslyRewarded))
            `when`(userMissionRepository.save(any())).thenAnswer { invocation: InvocationOnMock ->
                Mono.just(invocation.getArgument<com.miyou.app.domain.mission.model.UserMission>(0))
            }
            `when`(creditChargeUseCase.grantMissionReward(any(), any(), anyLong(), anyString()))
                .thenReturn(Mono.just(rewardTx))

            StepVerifier
                .create(service.completeMission(userId, missionId))
                .assertNext { result ->
                    assertThat(result.status()).isEqualTo(com.miyou.app.domain.mission.model.MissionStatus.REWARDED)
                }.verifyComplete()
        }
    }
}
