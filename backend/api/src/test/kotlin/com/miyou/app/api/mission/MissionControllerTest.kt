package com.miyou.app.api.mission

import com.miyou.app.application.mission.usecase.MissionCompletionUseCase
import com.miyou.app.application.mission.usecase.MissionQueryUseCase
import com.miyou.app.domain.auth.model.AuthenticatedUser
import com.miyou.app.domain.mission.model.MissionId
import com.miyou.app.domain.mission.model.MissionStatus
import com.miyou.app.domain.mission.model.MissionType
import com.miyou.app.domain.mission.model.UserMission
import com.miyou.app.fixture.MissionFixture
import com.miyou.app.fixture.UserIdFixture
import com.miyou.app.support.PermitAllSecurityTestConfig
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.mockito.Mockito.`when`
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.reactive.WebFluxTest
import org.springframework.context.annotation.Import
import org.springframework.http.HttpStatus
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.test.web.reactive.server.SecurityMockServerConfigurers.mockAuthentication
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.web.reactive.server.WebTestClient
import org.springframework.web.server.ResponseStatusException
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import java.time.Instant

@Import(PermitAllSecurityTestConfig::class)
@WebFluxTest(MissionController::class)
class MissionControllerTest {
    @Autowired
    private lateinit var webTestClient: WebTestClient

    @MockitoBean
    private lateinit var missionQueryUseCase: MissionQueryUseCase

    @MockitoBean
    private lateinit var missionCompletionUseCase: MissionCompletionUseCase

    @Test
    @DisplayName("getAllMissions는 수행 가능한 미션 목록을 반환한다")
    fun getAllMissions_returnsAvailableMissions() {
        // given - 수행 가능한 미션 2개를 설정
        val mission1 = MissionFixture.create()
        val mission2 = MissionFixture.create("referral-1", MissionType.REFERRAL, 300L, true)

        `when`(missionQueryUseCase.getAllMissions()).thenReturn(Flux.just(mission1, mission2))

        // when & then - /missions API 호출 결과 검증
        webTestClient
            .get()
            .uri("/missions")
            .exchange()
            .expectStatus()
            .isOk
            .expectBody()
            .jsonPath("$.length()")
            .isEqualTo(2)
    }

    @Test
    @DisplayName("completeMission은 보상이 지급된 미션 상태를 반환한다")
    fun completeMission_returnsRewardedMissionState() {
        // given - 사용자 ID와 미션 ID, 완료되어 보상이 지급된 사용자 미션 상태 설정
        val userId = UserIdFixture.create()
        val missionId = MissionId.of(MissionFixture.DEFAULT_MISSION_ID)
        val rewarded = UserMission(userId, missionId, MissionStatus.REWARDED, Instant.now(), Instant.now())

        `when`(missionCompletionUseCase.completeMission(userId, missionId)).thenReturn(Mono.just(rewarded))

        // when & then - 미션 완료 API 호출 및 REWARDED 상태 반환 검증
        webTestClient
            .post()
            .uri("/missions/{missionId}/complete?userId={userId}", missionId.value, userId)
            .exchange()
            .expectStatus()
            .isOk
            .expectBody()
            .jsonPath("$.status")
            .isEqualTo("REWARDED")
    }

    @Test
    @DisplayName("completeMission은 존재하지 않는 미션일 경우 404 에러를 반환한다")
    fun completeMission_returns404ForUnknownMission() {
        // given - 존재하지 않는 미션 ID 설정 및 404 에러 반환 모킹
        val userId = UserIdFixture.create()
        val missionId = MissionId.of("no-such-mission")

        `when`(missionCompletionUseCase.completeMission(userId, missionId))
            .thenReturn(Mono.error(ResponseStatusException(HttpStatus.NOT_FOUND, "mission not found")))

        // when & then - API 호출 시 404 에러가 반환되는지 검증
        webTestClient
            .post()
            .uri("/missions/{missionId}/complete?userId={userId}", missionId.value, userId)
            .exchange()
            .expectStatus()
            .isNotFound
    }

    @Test
    @DisplayName("completeMission은 쿼리 파라미터의 userId보다 인증된 사용자 정보의 userId를 우선한다 (비인증+OAuth 공존 대응)")
    fun completeMission_authenticatedPrincipal_overridesQueryParamUserId() {
        // given - 인증 객체 생성 및 모킹 처리
        val authenticatedUserId = "authenticated-mission-user"
        val queryParamUserId = "different-anonymous-user"
        val missionId = MissionId.of(MissionFixture.DEFAULT_MISSION_ID)
        val principal = AuthenticatedUser(authenticatedUserId)
        val rewarded = UserMission(authenticatedUserId, missionId, MissionStatus.REWARDED, Instant.now(), Instant.now())

        `when`(missionCompletionUseCase.completeMission(authenticatedUserId, missionId)).thenReturn(Mono.just(rewarded))

        // when & then - 인증된 컨텍스트 하에 미션 완료 API 호출 시, 인증된 userId가 우선적으로 사용되는지 검증
        webTestClient
            .mutateWith(mockAuthentication(UsernamePasswordAuthenticationToken(principal, null, emptyList())))
            .post()
            .uri("/missions/{missionId}/complete?userId={userId}", missionId.value, queryParamUserId)
            .exchange()
            .expectStatus()
            .isOk
            .expectBody()
            .jsonPath("$.status")
            .isEqualTo("REWARDED")
    }
}
