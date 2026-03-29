package com.miyou.app.application.mission.service;

import com.miyou.app.application.credit.usecase.CreditChargeUseCase;
import com.miyou.app.domain.credit.model.CreditTransaction;
import com.miyou.app.domain.dialogue.model.UserId;
import com.miyou.app.domain.mission.model.Mission;
import com.miyou.app.domain.mission.model.MissionId;
import com.miyou.app.domain.mission.model.MissionStatus;
import com.miyou.app.domain.mission.model.MissionType;
import com.miyou.app.domain.mission.model.UserMission;
import com.miyou.app.domain.mission.port.MissionRepository;
import com.miyou.app.domain.mission.port.UserMissionRepository;
import com.miyou.app.fixture.CreditTransactionFixture;
import com.miyou.app.fixture.MissionFixture;
import com.miyou.app.fixture.UserIdFixture;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("MissionApplicationService")
class MissionApplicationServiceTest {

	@Mock
	private MissionRepository missionRepository;

	@Mock
	private UserMissionRepository userMissionRepository;

	@Mock
	private CreditChargeUseCase creditChargeUseCase;

	private MissionApplicationService service;

	@BeforeEach
	void setUp() {
		service = new MissionApplicationService(
			missionRepository, userMissionRepository, creditChargeUseCase);
	}

	// ── getAllMissions ─────────────────────────────────────────────────────

	@Nested
	@DisplayName("getAllMissions()")
	class GetAllMissions {

		@Test
		@DisplayName("전체 미션 목록을 반환한다")
		void getAllMissions_returnsAll() {
			Mission m1 = MissionFixture.create();
			Mission m2 = MissionFixture.create("referral-1", MissionType.REFERRAL, 300L, false);
			when(missionRepository.findAll()).thenReturn(Flux.just(m1, m2));

			StepVerifier.create(service.getAllMissions())
				.expectNextCount(2)
				.verifyComplete();
		}

		@Test
		@DisplayName("미션이 없으면 빈 Flux를 반환한다")
		void getAllMissions_empty_returnsEmpty() {
			when(missionRepository.findAll()).thenReturn(Flux.empty());

			StepVerifier.create(service.getAllMissions())
				.verifyComplete();
		}
	}

	// ── getUserMissions ────────────────────────────────────────────────────

	@Nested
	@DisplayName("getUserMissions()")
	class GetUserMissions {

		@Test
		@DisplayName("유저의 진행 중인 미션 목록을 반환한다")
		void getUserMissions_returnsUserMissions() {
			UserId userId = UserIdFixture.create();
			UserMission um = MissionFixture.userMission(userId);
			when(userMissionRepository.findByUserId(userId)).thenReturn(Flux.just(um));

			StepVerifier.create(service.getUserMissions(userId))
				.assertNext(result -> assertThat(result.userId()).isEqualTo(userId))
				.verifyComplete();
		}
	}

	// ── completeMission ────────────────────────────────────────────────────

	@Nested
	@DisplayName("completeMission()")
	class CompleteMission {

		@Test
		@DisplayName("AVAILABLE 상태 유저가 미션 완료 시 REWARDED 상태가 되고 크레딧이 지급된다")
		void completeMission_success_grantsRewardAndRewardedStatus() {
			UserId userId = UserIdFixture.create();
			Mission mission = MissionFixture.create();
			MissionId missionId = mission.missionId();
			CreditTransaction rewardTx = CreditTransactionFixture.signupBonus(userId, 500L);

			when(missionRepository.findById(missionId)).thenReturn(Mono.just(mission));
			when(userMissionRepository.findByUserIdAndMissionId(userId, missionId))
				.thenReturn(Mono.empty());
			when(userMissionRepository.save(any())).thenAnswer(inv -> Mono.just(inv.getArgument(0)));
			when(creditChargeUseCase.grantMissionReward(eq(userId), eq(missionId), anyLong(), anyString()))
				.thenReturn(Mono.just(rewardTx));

			StepVerifier.create(service.completeMission(userId, missionId))
				.assertNext(result -> {
					assertThat(result.userId()).isEqualTo(userId);
					assertThat(result.missionId()).isEqualTo(missionId);
					assertThat(result.status()).isEqualTo(MissionStatus.REWARDED);
					assertThat(result.completedAt()).isNotNull();
					assertThat(result.rewardedAt()).isNotNull();
				})
				.verifyComplete();

			verify(creditChargeUseCase).grantMissionReward(
				eq(userId), eq(missionId), eq(MissionFixture.DEFAULT_REWARD), eq("SHARE_SERVICE"));
		}

		@Test
		@DisplayName("존재하지 않는 미션 ID는 404 반환")
		void completeMission_notFound_throws404() {
			UserId userId = UserIdFixture.create();
			MissionId unknownId = MissionId.of("unknown-mission");
			when(missionRepository.findById(unknownId)).thenReturn(Mono.empty());

			StepVerifier.create(service.completeMission(userId, unknownId))
				.expectErrorMatches(e ->
					e instanceof ResponseStatusException rse &&
					rse.getStatusCode() == HttpStatus.NOT_FOUND)
				.verify();

			verify(creditChargeUseCase, never()).grantMissionReward(any(), any(), anyLong(), anyString());
		}

		@Test
		@DisplayName("이미 REWARDED 상태의 비반복 미션은 409 Conflict 반환")
		void completeMission_alreadyRewarded_nonRepeatable_throws409() {
			UserId userId = UserIdFixture.create();
			Mission mission = MissionFixture.create(
				"one-time-mission", MissionType.SHARE_SERVICE, 500L, false);
			MissionId missionId = mission.missionId();
			UserMission completedWithSameId = new UserMission(
				userId, missionId, MissionStatus.REWARDED,
				java.time.Instant.now(), java.time.Instant.now());

			when(missionRepository.findById(missionId)).thenReturn(Mono.just(mission));
			when(userMissionRepository.findByUserIdAndMissionId(userId, missionId))
				.thenReturn(Mono.just(completedWithSameId));

			StepVerifier.create(service.completeMission(userId, missionId))
				.expectErrorMatches(e ->
					e instanceof ResponseStatusException rse &&
					rse.getStatusCode() == HttpStatus.CONFLICT)
				.verify();

			verify(creditChargeUseCase, never()).grantMissionReward(any(), any(), anyLong(), anyString());
		}

		@Test
		@DisplayName("반복 가능 미션은 이미 REWARDED 상태여도 다시 완료 가능하다")
		void completeMission_repeatable_alreadyRewarded_succeeds() {
			UserId userId = UserIdFixture.create();
			Mission repeatableMission = MissionFixture.create(
				"daily-mission", MissionType.TASK_COMPLETION, 100L, true);
			MissionId missionId = repeatableMission.missionId();
			UserMission previouslyRewarded = new UserMission(
				userId, missionId, MissionStatus.REWARDED,
				java.time.Instant.now().minusSeconds(100),
				java.time.Instant.now().minusSeconds(90));
			CreditTransaction rewardTx = CreditTransactionFixture.signupBonus(userId, 100L);

			when(missionRepository.findById(missionId)).thenReturn(Mono.just(repeatableMission));
			when(userMissionRepository.findByUserIdAndMissionId(userId, missionId))
				.thenReturn(Mono.just(previouslyRewarded));
			when(userMissionRepository.save(any())).thenAnswer(inv -> Mono.just(inv.getArgument(0)));
			when(creditChargeUseCase.grantMissionReward(any(), any(), anyLong(), anyString()))
				.thenReturn(Mono.just(rewardTx));

			StepVerifier.create(service.completeMission(userId, missionId))
				.assertNext(result -> assertThat(result.status()).isEqualTo(MissionStatus.REWARDED))
				.verifyComplete();
		}
	}
}
