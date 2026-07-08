package com.miyou.app.application.mission.service

import com.miyou.app.domain.mission.exception.MissionAlreadyCompletedException
import com.miyou.app.domain.mission.exception.MissionNotFoundException
import com.miyou.app.domain.mission.port.CreditRewardCommand
import com.miyou.app.domain.mission.port.CreditRewardResult
import com.miyou.app.domain.mission.port.MissionCreditChargingPort
import com.miyou.app.fixture.MissionFixture
import com.miyou.app.fixture.UserIdFixture
import com.miyou.app.support.anyValue
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mock
import org.mockito.Mockito.never
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
import org.mockito.invocation.InvocationOnMock
import org.mockito.junit.jupiter.MockitoExtension
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
    private lateinit var creditChargingPort: MissionCreditChargingPort

    private lateinit var service: MissionApplicationService

    @BeforeEach
    fun setUp() {
        service = MissionApplicationService(missionRepository, userMissionRepository, creditChargingPort)
    }

    @Test
    @DisplayName("getAllMissions returns the repository result")
    fun getAllMissions_returnsRepositoryResult() {
        val missions =
            Flux.just(
                MissionFixture.create(),
                MissionFixture.create("referral", com.miyou.app.domain.mission.model.MissionType.REFERRAL, 300L, false)
            )

        `when`(missionRepository.findAll()).thenReturn(missions)

        StepVerifier.create(service.getAllMissions()).expectNextCount(2).verifyComplete()
    }

    @Test
    @DisplayName("getUserMissions returns the user's mission states")
    fun getUserMissions_returnsUserMissionStates() {
        val userId = UserIdFixture.create()
        val userMission = MissionFixture.userMission(userId)

        `when`(userMissionRepository.findByUserId(userId)).thenReturn(Flux.just(userMission))

        StepVerifier
            .create(service.getUserMissions(userId))
            .assertNext { result -> assertThat(result.userId).isEqualTo(userId) }
            .verifyComplete()
    }

    @Test
    @DisplayName("completeMission rewards an available mission")
    fun completeMission_rewardsAvailableMission() {
        val userId = UserIdFixture.create()
        val mission = MissionFixture.create()
        val missionId = mission.missionId
        val rewardResult = CreditRewardResult("tx-mission-reward")

        `when`(missionRepository.findById(missionId)).thenReturn(Mono.just(mission))
        `when`(userMissionRepository.findByUserIdAndMissionId(userId, missionId)).thenReturn(Mono.empty())
        `when`(userMissionRepository.save(anyValue()))
            .thenAnswer { invocation: InvocationOnMock ->
                Mono.just(invocation.getArgument<com.miyou.app.domain.mission.model.UserMission>(0))
            }
        `when`(
            creditChargingPort.grantReward(
                CreditRewardCommand(userId, missionId, mission.rewardAmount, mission.type.name),
            ),
        ).thenReturn(Mono.just(rewardResult))

        StepVerifier
            .create(service.completeMission(userId, missionId))
            .assertNext { result ->
                assertThat(result.userId).isEqualTo(userId)
                assertThat(result.missionId).isEqualTo(missionId)
                assertThat(result.status).isEqualTo(com.miyou.app.domain.mission.model.MissionStatus.REWARDED)
                assertThat(result.completedAt).isNotNull()
                assertThat(result.rewardedAt).isNotNull()
            }.verifyComplete()
    }

    @Test
    @DisplayName("completeMission returns 404 when the mission does not exist")
    fun completeMission_returns404WhenMissionDoesNotExist() {
        val userId = UserIdFixture.create()
        val missionId =
            com.miyou.app.domain.mission.model.MissionId
                .of("unknown-mission")

        `when`(missionRepository.findById(missionId)).thenReturn(Mono.empty())

        StepVerifier
            .create(service.completeMission(userId, missionId))
            .expectErrorMatches { error ->
                error is MissionNotFoundException
            }.verify()

        verify(
            creditChargingPort,
            never()
        ).grantReward(anyValue())
    }

    @Test
    @DisplayName("completeMission returns 409 for an already rewarded non-repeatable mission")
    fun completeMission_returns409ForAlreadyRewardedNonRepeatableMission() {
        val userId = UserIdFixture.create()
        val mission =
            MissionFixture.create(
                "one-time-mission",
                com.miyou.app.domain.mission.model.MissionType.SHARE_SERVICE,
                500L,
                false,
            )
        val missionId = mission.missionId
        val rewardedMission =
            com.miyou.app.domain.mission.model.UserMission(
                userId,
                missionId,
                com.miyou.app.domain.mission.model.MissionStatus.REWARDED,
                Instant.now(),
                Instant.now(),
            )

        `when`(missionRepository.findById(missionId)).thenReturn(Mono.just(mission))
        `when`(userMissionRepository.findByUserIdAndMissionId(userId, missionId)).thenReturn(Mono.just(rewardedMission))

        StepVerifier
            .create(service.completeMission(userId, missionId))
            .expectErrorMatches { error ->
                error is MissionAlreadyCompletedException
            }.verify()
    }
}
