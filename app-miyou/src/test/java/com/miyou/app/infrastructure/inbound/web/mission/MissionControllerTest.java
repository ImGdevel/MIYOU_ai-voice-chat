package com.miyou.app.infrastructure.inbound.web.mission;

import java.time.Instant;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.WebFluxTest;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.springframework.web.server.ResponseStatusException;

import com.miyou.app.application.mission.usecase.MissionCompletionUseCase;
import com.miyou.app.application.mission.usecase.MissionQueryUseCase;
import com.miyou.app.domain.dialogue.model.UserId;
import com.miyou.app.domain.mission.model.Mission;
import com.miyou.app.domain.mission.model.MissionId;
import com.miyou.app.domain.mission.model.MissionStatus;
import com.miyou.app.domain.mission.model.MissionType;
import com.miyou.app.domain.mission.model.UserMission;
import com.miyou.app.fixture.MissionFixture;
import com.miyou.app.fixture.UserIdFixture;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@WebFluxTest(MissionController.class)
@DisplayName("MissionController WebFlux 테스트")
class MissionControllerTest {

	@Autowired
	private WebTestClient webTestClient;

	@MockitoBean
	private MissionQueryUseCase missionQueryUseCase;

	@MockitoBean
	private MissionCompletionUseCase missionCompletionUseCase;

	// ── GET /missions ──────────────────────────────────────────────────────

	@Nested
	@DisplayName("GET /missions")
	class GetAllMissions {

		@Test
		@DisplayName("전체 미션 목록을 200으로 반환한다")
		void getAllMissions_returns200WithList() {
			Mission m1 = MissionFixture.create();
			Mission m2 = MissionFixture.create("referral-1", MissionType.REFERRAL, 300L, true);
			when(missionQueryUseCase.getAllMissions()).thenReturn(Flux.just(m1, m2));

			webTestClient.get()
				.uri("/missions")
				.exchange()
				.expectStatus().isOk()
				.expectBody()
				.jsonPath("$.length()").isEqualTo(2)
				.jsonPath("$[0].missionId").isEqualTo(MissionFixture.DEFAULT_MISSION_ID)
				.jsonPath("$[0].rewardAmount").isEqualTo(MissionFixture.DEFAULT_REWARD)
				.jsonPath("$[0].repeatable").isEqualTo(false)
				.jsonPath("$[1].missionId").isEqualTo("referral-1")
				.jsonPath("$[1].repeatable").isEqualTo(true);
		}

		@Test
		@DisplayName("미션이 없으면 빈 배열을 반환한다")
		void getAllMissions_empty_returnsEmptyArray() {
			when(missionQueryUseCase.getAllMissions()).thenReturn(Flux.empty());

			webTestClient.get()
				.uri("/missions")
				.exchange()
				.expectStatus().isOk()
				.expectBody()
				.jsonPath("$.length()").isEqualTo(0);
		}
	}

	// ── GET /missions/my ───────────────────────────────────────────────────

	@Nested
	@DisplayName("GET /missions/my")
	class GetUserMissions {

		@Test
		@DisplayName("유저의 미션 진행 현황을 반환한다")
		void getUserMissions_returns200WithUserMissions() {
			UserId userId = UserIdFixture.create();
			UserMission um = MissionFixture.userMission(userId);
			when(missionQueryUseCase.getUserMissions(eq(userId))).thenReturn(Flux.just(um));

			webTestClient.get()
				.uri("/missions/my?userId={id}", userId.value())
				.exchange()
				.expectStatus().isOk()
				.expectBody()
				.jsonPath("$.length()").isEqualTo(1)
				.jsonPath("$[0].userId").isEqualTo(userId.value())
				.jsonPath("$[0].status").isEqualTo("AVAILABLE");
		}

		@Test
		@DisplayName("userId 파라미터가 없으면 400을 반환한다")
		void getUserMissions_missingUserId_returns400() {
			webTestClient.get()
				.uri("/missions/my")
				.exchange()
				.expectStatus().isBadRequest();
		}
	}

	// ── POST /missions/{missionId}/complete ────────────────────────────────

	@Nested
	@DisplayName("POST /missions/{missionId}/complete")
	class CompleteMission {

		@Test
		@DisplayName("미션 완료 처리 성공 시 200과 REWARDED 상태를 반환한다")
		void completeMission_success_returns200WithRewarded() {
			UserId userId = UserIdFixture.create();
			MissionId missionId = MissionId.of(MissionFixture.DEFAULT_MISSION_ID);
			UserMission rewarded = new UserMission(
				userId, missionId, MissionStatus.REWARDED, Instant.now(), Instant.now());

			when(missionCompletionUseCase.completeMission(eq(userId), eq(missionId)))
				.thenReturn(Mono.just(rewarded));

			webTestClient.post()
				.uri("/missions/{missionId}/complete?userId={userId}",
					missionId.value(),
					userId.value())
				.exchange()
				.expectStatus().isOk()
				.expectBody()
				.jsonPath("$.status").isEqualTo("REWARDED")
				.jsonPath("$.userId").isEqualTo(userId.value())
				.jsonPath("$.missionId").isEqualTo(missionId.value())
				.jsonPath("$.completedAt").isNotEmpty()
				.jsonPath("$.rewardedAt").isNotEmpty();
		}

		@Test
		@DisplayName("존재하지 않는 미션은 404를 반환한다")
		void completeMission_notFound_returns404() {
			UserId userId = UserIdFixture.create();
			MissionId unknownId = MissionId.of("no-such-mission");

			when(missionCompletionUseCase.completeMission(any(), eq(unknownId)))
				.thenReturn(Mono.error(
					new ResponseStatusException(HttpStatus.NOT_FOUND, "미션을 찾을 수 없습니다")));

			webTestClient.post()
				.uri("/missions/{missionId}/complete?userId={userId}",
					unknownId.value(),
					userId.value())
				.exchange()
				.expectStatus().isNotFound();
		}

		@Test
		@DisplayName("이미 완료된 비반복 미션은 409를 반환한다")
		void completeMission_alreadyCompleted_returns409() {
			UserId userId = UserIdFixture.create();
			MissionId missionId = MissionId.of(MissionFixture.DEFAULT_MISSION_ID);

			when(missionCompletionUseCase.completeMission(any(), eq(missionId)))
				.thenReturn(Mono.error(
					new ResponseStatusException(HttpStatus.CONFLICT, "이미 완료된 미션입니다")));

			webTestClient.post()
				.uri("/missions/{missionId}/complete?userId={userId}",
					missionId.value(),
					userId.value())
				.exchange()
				.expectStatus().isEqualTo(409);
		}

		@Test
		@DisplayName("userId 파라미터가 없으면 400을 반환한다")
		void completeMission_missingUserId_returns400() {
			webTestClient.post()
				.uri("/missions/{missionId}/complete", MissionFixture.DEFAULT_MISSION_ID)
				.exchange()
				.expectStatus().isBadRequest();
		}
	}
}
