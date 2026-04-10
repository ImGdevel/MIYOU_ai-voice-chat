package com.miyou.app.infrastructure.inbound.web.mission

import com.miyou.app.application.mission.usecase.MissionCompletionUseCase
import com.miyou.app.application.mission.usecase.MissionQueryUseCase
import com.miyou.app.domain.dialogue.model.UserId
import com.miyou.app.domain.mission.model.MissionId
import com.miyou.app.infrastructure.inbound.web.mission.dto.MissionResponse
import com.miyou.app.infrastructure.inbound.web.mission.dto.UserMissionResponse
import jakarta.validation.constraints.NotBlank
import org.springframework.http.HttpStatus
import org.springframework.validation.annotation.Validated
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono

@Validated
@RestController
@RequestMapping("/missions")
class MissionController(
    private val missionQueryUseCase: MissionQueryUseCase,
    private val missionCompletionUseCase: MissionCompletionUseCase,
) {
    @GetMapping
    fun getAllMissions(): Flux<MissionResponse> = missionQueryUseCase.getAllMissions().map(MissionResponse::from)

    @GetMapping("/my")
    fun getUserMissions(
        @RequestParam @NotBlank userId: String,
    ): Flux<UserMissionResponse> = missionQueryUseCase.getUserMissions(UserId.of(userId)).map(UserMissionResponse::from)

    @PostMapping("/{missionId}/complete")
    @ResponseStatus(HttpStatus.OK)
    fun completeMission(
        @PathVariable missionId: String,
        @RequestParam @NotBlank userId: String,
    ): Mono<UserMissionResponse> =
        missionCompletionUseCase
            .completeMission(
                UserId.of(userId),
                MissionId.of(missionId),
            ).map(UserMissionResponse::from)
}
