package com.miyou.app.api.mission.docs

import com.miyou.app.api.mission.dto.MissionResponse
import com.miyou.app.api.mission.dto.UserMissionResponse
import com.miyou.app.domain.auth.model.AuthenticatedUser
import com.miyou.app.exception.ErrorResponse
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.security.core.annotation.AuthenticationPrincipal
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono

@Tag(name = "미션 API", description = "시스템 내 미션 목록 조회 및 미션 수행/완료 처리를 담당하는 REST API")
interface MissionApi {
    @Operation(
        summary = "전체 미션 목록 조회",
        description = "시스템에 등록된 수행 가능한 모든 미션들의 정보를 가져옵니다.",
    )
    @ApiResponse(
        responseCode = "200",
        description = "조회 성공",
        content = [Content(schema = Schema(implementation = MissionResponse::class))],
    )
    fun getAllMissions(): Flux<MissionResponse>

    @Operation(
        summary = "나의 미션 수행 현황 조회",
        description = "특정 사용자의 현재 미션 진행 상황 및 완료 현황 목록을 조회합니다.",
    )
    @ApiResponses(
        value = [
            ApiResponse(
                responseCode = "200",
                description = "조회 성공",
                content = [Content(schema = Schema(implementation = UserMissionResponse::class))],
            ),
            ApiResponse(
                responseCode = "400",
                description = "잘못된 요청 (사용자 ID 및 인증 토큰 누락)",
                content = [Content(schema = Schema(implementation = ErrorResponse::class))],
            ),
            ApiResponse(
                responseCode = "401",
                description = "인증 실패 또는 권한 없음",
                content = [Content(schema = Schema(implementation = ErrorResponse::class))],
            ),
        ],
    )
    fun getUserMissions(
        @Parameter(description = "대상 사용자 고유 ID", example = "user-123")
        userId: String?,
        @AuthenticationPrincipal principal: AuthenticatedUser?,
    ): Flux<UserMissionResponse>

    @Operation(
        summary = "미션 완료 처리 및 보상 획득",
        description = "진행 완료된 특정 미션을 완료 상태로 변경하고, 미션에 지정된 크레딧 보상을 지급받습니다.",
    )
    @ApiResponses(
        value = [
            ApiResponse(
                responseCode = "200",
                description = "미션 완료 처리 및 보상 지급 성공",
                content = [Content(schema = Schema(implementation = UserMissionResponse::class))],
            ),
            ApiResponse(
                responseCode = "400",
                description = "잘못된 요청 파라미터",
                content = [Content(schema = Schema(implementation = ErrorResponse::class))],
            ),
            ApiResponse(
                responseCode = "404",
                description = "존재하지 않는 미션 ID",
                content = [Content(schema = Schema(implementation = ErrorResponse::class))],
            ),
            ApiResponse(
                responseCode = "409",
                description = "이미 완료되거나 보상이 지급된 미션",
                content = [Content(schema = Schema(implementation = ErrorResponse::class))],
            ),
        ],
    )
    fun completeMission(
        @Parameter(description = "완료 처리할 미션 ID", example = "mission_daily_checkin")
        missionId: String,
        @Parameter(description = "대상 사용자 고유 ID", example = "user-123")
        userId: String?,
        @AuthenticationPrincipal principal: AuthenticatedUser?,
    ): Mono<UserMissionResponse>
}
