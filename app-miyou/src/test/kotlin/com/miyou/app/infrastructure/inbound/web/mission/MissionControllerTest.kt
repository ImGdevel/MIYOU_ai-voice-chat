package com.miyou.app.infrastructure.inbound.web.mission

import com.miyou.app.application.mission.usecase.MissionCompletionUseCase
import com.miyou.app.application.mission.usecase.MissionQueryUseCase
import com.miyou.app.domain.mission.model.MissionId
import com.miyou.app.domain.mission.model.MissionStatus
import com.miyou.app.domain.mission.model.MissionType
import com.miyou.app.domain.mission.model.UserMission
import com.miyou.app.fixture.MissionFixture
import com.miyou.app.fixture.UserIdFixture
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.mockito.Mockito.`when`
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.reactive.WebFluxTest
import org.springframework.http.HttpStatus
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.web.reactive.server.WebTestClient
import org.springframework.web.server.ResponseStatusException
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import java.time.Instant

@WebFluxTest(MissionController::class)
class MissionControllerTest {
    @Autowired
    private lateinit var webTestClient: WebTestClient

    @MockitoBean
    private lateinit var missionQueryUseCase: MissionQueryUseCase

    @MockitoBean
    private lateinit var missionCompletionUseCase: MissionCompletionUseCase

    @Test
    @DisplayName("getAllMissions returns the available missions")
    fun getAllMissions_returnsAvailableMissions() {
        val mission1 = MissionFixture.create()
        val mission2 = MissionFixture.create("referral-1", MissionType.REFERRAL, 300L, true)

        `when`(missionQueryUseCase.getAllMissions()).thenReturn(Flux.just(mission1, mission2))

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
    @DisplayName("completeMission returns the rewarded mission state")
    fun completeMission_returnsRewardedMissionState() {
        val userId = UserIdFixture.create()
        val missionId = MissionId.of(MissionFixture.DEFAULT_MISSION_ID)
        val rewarded = UserMission(userId, missionId, MissionStatus.REWARDED, Instant.now(), Instant.now())

        `when`(missionCompletionUseCase.completeMission(userId, missionId)).thenReturn(Mono.just(rewarded))

        webTestClient
            .post()
            .uri("/missions/{missionId}/complete?userId={userId}", missionId.value, userId.value)
            .exchange()
            .expectStatus()
            .isOk
            .expectBody()
            .jsonPath("$.status")
            .isEqualTo("REWARDED")
    }

    @Test
    @DisplayName("completeMission returns 404 for an unknown mission")
    fun completeMission_returns404ForUnknownMission() {
        val userId = UserIdFixture.create()
        val missionId = MissionId.of("no-such-mission")

        `when`(missionCompletionUseCase.completeMission(userId, missionId))
            .thenReturn(Mono.error(ResponseStatusException(HttpStatus.NOT_FOUND, "mission not found")))

        webTestClient
            .post()
            .uri("/missions/{missionId}/complete?userId={userId}", missionId.value, userId.value)
            .exchange()
            .expectStatus()
            .isNotFound
    }
}
