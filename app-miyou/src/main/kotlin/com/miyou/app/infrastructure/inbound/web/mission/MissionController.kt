package com.miyou.app.infrastructure.inbound.web.mission

import com.miyou.app.application.mission.usecase.MissionCompletionUseCase
import com.miyou.app.application.mission.usecase.MissionQueryUseCase
import com.miyou.app.domain.auth.model.AuthenticatedUser
import com.miyou.app.domain.mission.model.MissionId
import com.miyou.app.infrastructure.inbound.web.mission.dto.MissionResponse
import com.miyou.app.infrastructure.inbound.web.mission.dto.UserMissionResponse
import org.springframework.http.HttpStatus
import org.springframework.security.core.annotation.AuthenticationPrincipal
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
        @RequestParam(required = false) userId: String?,
        @AuthenticationPrincipal principal: AuthenticatedUser?,
    ): Flux<UserMissionResponse> {
        val resolvedUserId =
            principal?.userId
                ?: userId?.takeIf { it.isNotBlank() && it.length <= 128 }
                ?: return Flux.error(
                    org.springframework.web.server
                        .ResponseStatusException(HttpStatus.BAD_REQUEST, "userId is required")
                )
        return missionQueryUseCase.getUserMissions(resolvedUserId).map(UserMissionResponse::from)
    }

    @PostMapping("/{missionId}/complete")
    @ResponseStatus(HttpStatus.OK)
    fun completeMission(
        @PathVariable missionId: String,
        @RequestParam(required = false) userId: String?,
        @AuthenticationPrincipal principal: AuthenticatedUser?,
    ): Mono<UserMissionResponse> {
        val resolvedUserId =
            principal?.userId
                ?: userId?.takeIf { it.isNotBlank() && it.length <= 128 }
                ?: return Mono.error(
                    org.springframework.web.server
                        .ResponseStatusException(HttpStatus.BAD_REQUEST, "userId is required")
                )
        return missionCompletionUseCase
            .completeMission(
                resolvedUserId,
                MissionId.of(missionId),
            ).map(UserMissionResponse::from)
    }
}
