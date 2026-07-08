package com.miyou.app.api.auth.docs

import com.miyou.app.api.auth.dto.AuthTokenResponse
import com.miyou.app.api.auth.dto.RefreshTokenRequest
import com.miyou.app.exception.ErrorResponse
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import reactor.core.publisher.Mono

@Tag(name = "인증 API", description = "토큰 재발급 및 로그아웃을 처리하는 인증 관련 REST API")
interface AuthApi {
    @Operation(
        summary = "액세스 토큰 재발급",
        description = "만료된 액세스 토큰을 갱신하기 위해 유효한 리프레시 토큰으로 새 토큰 세트를 발급받습니다.",
    )
    @ApiResponses(
        value = [
            ApiResponse(
                responseCode = "200",
                description = "토큰 재발급 성공",
                content = [Content(schema = Schema(implementation = AuthTokenResponse::class))],
            ),
            ApiResponse(
                responseCode = "401",
                description = "인증 실패 (유효하지 않거나 만료된 리프레시 토큰)",
                content = [Content(schema = Schema(implementation = ErrorResponse::class))],
            ),
        ],
    )
    fun refresh(
        @Valid request: RefreshTokenRequest,
    ): Mono<AuthTokenResponse>

    @Operation(
        summary = "로그아웃",
        description = "사용 중인 리프레시 토큰을 무효화하여 사용자를 로그아웃 처리합니다.",
    )
    @ApiResponses(
        value = [
            ApiResponse(
                responseCode = "204",
                description = "로그아웃 성공 (반환값 없음)",
            ),
            ApiResponse(
                responseCode = "401",
                description = "인증 실패 (유효하지 않은 리프레시 토큰)",
                content = [Content(schema = Schema(implementation = ErrorResponse::class))],
            ),
        ],
    )
    fun logout(
        @Valid request: RefreshTokenRequest,
    ): Mono<Void>
}
