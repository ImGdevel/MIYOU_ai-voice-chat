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
    @DisplayName("getAllMissions는 저장소의 모든 미션 목록을 반환한다")
    fun getAllMissions_returnsRepositoryResult() {
        // given - 두 개의 테스트 미션 설정
        val missions =
            Flux.just(
                MissionFixture.create(),
                MissionFixture.create("referral", com.miyou.app.domain.mission.model.MissionType.REFERRAL, 300L, false)
            )

        `when`(missionRepository.findAll()).thenReturn(missions)

        // when & then - 전체 미션 목록 조회 검증
        StepVerifier.create(service.getAllMissions()).expectNextCount(2).verifyComplete()
    }

    @Test
    @DisplayName("getUserMissions는 사용자의 미션 진행 현황을 반환한다")
    fun getUserMissions_returnsUserMissionStates() {
        // given - 사용자 ID 및 사용자의 미션 현황 데이터 준비
        val userId = UserIdFixture.create()
        val userMission = MissionFixture.userMission(userId)

        `when`(userMissionRepository.findByUserId(userId)).thenReturn(Flux.just(userMission))

        // when & then - 사용자의 미션 현황 조회 결과 검증
        StepVerifier
            .create(service.getUserMissions(userId))
            .assertNext { result -> assertThat(result.userId).isEqualTo(userId) }
            .verifyComplete()
    }

    @Test
    @DisplayName("completeMission은 수행 가능한 미션 완료 시 보상을 지급한다")
    fun completeMission_rewardsAvailableMission() {
        // given - 사용자 ID, 미션, 보상 트랜잭션 정보 준비
        val userId = UserIdFixture.create()
        val mission = MissionFixture.create()
        val missionId = mission.missionId
        val rewardResult = CreditRewardResult("tx-mission-reward")

        // 미션이 존재하고 아직 완료되지 않은 상태를 모킹
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

        // when - 미션 완료 처리 실행
        StepVerifier
            .create(service.completeMission(userId, missionId))
            .assertNext { result ->
                // then - 완료 상태(REWARDED)와 시간 정보가 올바르게 업데이트 되었는지 검증
                assertThat(result.userId).isEqualTo(userId)
                assertThat(result.missionId).isEqualTo(missionId)
                assertThat(result.status).isEqualTo(com.miyou.app.domain.mission.model.MissionStatus.REWARDED)
                assertThat(result.completedAt).isNotNull()
                assertThat(result.rewardedAt).isNotNull()
            }.verifyComplete()
    }

    @Test
    @DisplayName("completeMission은 미션이 존재하지 않을 때 404 예외를 발생시킨다")
    fun completeMission_returns404WhenMissionDoesNotExist() {
        // given - 존재하지 않는 미션 ID 설정 및 모킹
        val userId = UserIdFixture.create()
        val missionId =
            com.miyou.app.domain.mission.model.MissionId
                .of("unknown-mission")

        `when`(missionRepository.findById(missionId)).thenReturn(Mono.empty())

        // when & then - 존재하지 않는 미션 완료 시 MissionNotFoundException 에러가 발생하는지 검증
        StepVerifier
            .create(service.completeMission(userId, missionId))
            .expectErrorMatches { error ->
                error is MissionNotFoundException
            }.verify()

        // 보상 지급 기능이 호출되지 않았는지 확인
        verify(
            creditChargingPort,
            never()
        ).grantReward(anyValue())
    }

    @Test
    @DisplayName("completeMission은 이미 보상이 지급된 반복 불가능 미션에 대해 409 예외를 발생시킨다")
    fun completeMission_returns409ForAlreadyRewardedNonRepeatableMission() {
        // given - 이미 보상이 완료된 1회성(반복 불가) 미션 및 사용자 미션 상태 준비
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

        // when & then - 이미 완료된 미션 시도 시 MissionAlreadyCompletedException 발생하는지 검증
        StepVerifier
            .create(service.completeMission(userId, missionId))
            .expectErrorMatches { error ->
                error is MissionAlreadyCompletedException
            }.verify()
    }
}
