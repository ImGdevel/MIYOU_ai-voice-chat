package com.miyou.app.api.credit.docs

import com.miyou.app.api.credit.dto.ChargeByPaymentRequest
import com.miyou.app.api.credit.dto.CreditTransactionResponse
import com.miyou.app.api.credit.dto.UserCreditResponse
import com.miyou.app.domain.auth.model.AuthenticatedUser
import com.miyou.app.exception.ErrorResponse
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import org.springframework.security.core.annotation.AuthenticationPrincipal
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono

@Tag(name = "크레딧 API", description = "사용자의 크레딧 잔액 조회, 거래 내역 조회 및 결제를 통한 충전을 관리하는 REST API")
interface CreditApi {
    @Operation(
        summary = "보유 크레딧 잔액 조회",
        description = "사용자의 현재 크레딧 잔액을 조회합니다. 사용자 ID를 쿼리 파라미터로 제공하거나 인증 컨텍스트를 활용합니다.",
    )
    @ApiResponses(
        value = [
            ApiResponse(
                responseCode = "200",
                description = "조회 성공",
                content = [Content(schema = Schema(implementation = UserCreditResponse::class))],
            ),
            ApiResponse(
                responseCode = "400",
                description = "잘못된 요청 (사용자 ID 파라미터 및 인증 토큰 누락)",
                content = [Content(schema = Schema(implementation = ErrorResponse::class))],
            ),
            ApiResponse(
                responseCode = "404",
                description = "사용자 크레딧 정보를 찾을 수 없음",
                content = [Content(schema = Schema(implementation = ErrorResponse::class))],
            ),
        ],
    )
    fun getBalance(
        @Parameter(description = "대상 사용자 고유 ID", example = "user-123")
        userId: String?,
        @AuthenticationPrincipal principal: AuthenticatedUser?,
    ): Mono<UserCreditResponse>

    @Operation(
        summary = "크레딧 거래 내역 목록 조회",
        description = "사용자의 크레딧 거래 내역(충전, 소모, 환불)을 페이징하여 조회합니다.",
    )
    @ApiResponses(
        value = [
            ApiResponse(
                responseCode = "200",
                description = "조회 성공 (배열 반환)",
                content = [Content(schema = Schema(implementation = CreditTransactionResponse::class))],
            ),
            ApiResponse(
                responseCode = "400",
                description = "잘못된 요청",
                content = [Content(schema = Schema(implementation = ErrorResponse::class))],
            ),
        ],
    )
    fun getTransactions(
        @Parameter(description = "대상 사용자 고유 ID", example = "user-123")
        userId: String?,
        @Parameter(description = "페이지 번호 (0부터 시작)", example = "0")
        page: Int,
        @Parameter(description = "한 페이지에 노출할 거래 내역 개수", example = "20")
        size: Int,
        @AuthenticationPrincipal principal: AuthenticatedUser?,
    ): Flux<CreditTransactionResponse>

    @Operation(
        summary = "결제 승인을 통한 크레딧 충전",
        description = "토스페이먼츠 등 외부 PG 결제 완료 후, 승인 키를 활용해 최종 결제를 확인하고 크레딧을 충전합니다.",
    )
    @ApiResponses(
        value = [
            ApiResponse(
                responseCode = "201",
                description = "크레딧 충전 성공 (생성된 거래 내역 반환)",
                content = [Content(schema = Schema(implementation = CreditTransactionResponse::class))],
            ),
            ApiResponse(
                responseCode = "400",
                description = "잘못된 입력값 검증 실패",
                content = [Content(schema = Schema(implementation = ErrorResponse::class))],
            ),
            ApiResponse(
                responseCode = "422",
                description = "지원하지 않는 PG사 또는 PG 결제 연동 실패",
                content = [Content(schema = Schema(implementation = ErrorResponse::class))],
            ),
        ],
    )
    fun chargeByPayment(
        @Valid request: ChargeByPaymentRequest,
        @AuthenticationPrincipal principal: AuthenticatedUser?,
    ): Mono<CreditTransactionResponse>
}
