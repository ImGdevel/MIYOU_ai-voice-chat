package com.miyou.app.infrastructure.inbound.web.mission;

import lombok.RequiredArgsConstructor;

import org.springframework.http.HttpStatus;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.miyou.app.application.mission.usecase.MissionCompletionUseCase;
import com.miyou.app.application.mission.usecase.MissionQueryUseCase;
import com.miyou.app.domain.dialogue.model.UserId;
import com.miyou.app.domain.mission.model.MissionId;
import com.miyou.app.infrastructure.inbound.web.mission.dto.MissionResponse;
import com.miyou.app.infrastructure.inbound.web.mission.dto.UserMissionResponse;
import jakarta.validation.constraints.NotBlank;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/** 미션 조회 및 완료 처리 엔드포인트 */
@Validated
@RestController
@RequiredArgsConstructor
@RequestMapping("/missions")
public class MissionController {

	private final MissionQueryUseCase missionQueryUseCase;
	private final MissionCompletionUseCase missionCompletionUseCase;

	/** 전체 미션 목록을 조회합니다. */
	@GetMapping
	public Flux<MissionResponse> getAllMissions() {
		return missionQueryUseCase.getAllMissions().map(MissionResponse::from);
	}

	/** 유저의 미션 진행 현황을 조회합니다. */
	@GetMapping("/my")
	public Flux<UserMissionResponse> getUserMissions(@RequestParam @NotBlank String userId) {
		return missionQueryUseCase.getUserMissions(UserId.of(userId)).map(UserMissionResponse::from);
	}

	/** 미션을 완료 처리하고 크레딧을 지급합니다. */
	@PostMapping("/{missionId}/complete")
	@ResponseStatus(HttpStatus.OK)
	public Mono<UserMissionResponse> completeMission(
		@PathVariable String missionId,
		@RequestParam @NotBlank String userId) {
		return missionCompletionUseCase.completeMission(UserId.of(userId), MissionId.of(missionId))
			.map(UserMissionResponse::from);
	}
}
