package com.miyou.app.api.credit.dto

import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.Positive
import jakarta.validation.constraints.Size

@Schema(description = "결제 기반 크레딧 충전 요청 DTO")
data class ChargeByPaymentRequest(
    @field:Size(max = 128, message = "userId too long")
    @field:Schema(description = "충전 대상 사용자 ID (미인증 시 필수, 인증 시 principal 사용)", example = "user-123")
    val userId: String? = null,
    @field:Schema(description = "결제 승인용 고유 키", example = "pay_key_abcdefghijklmnopqrstuvwx")
    val paymentKey: String,
    @field:Schema(description = "주문 번호 (서버 발급 고유 ID)", example = "order-20260708-0001")
    val orderId: String,
    @field:Schema(description = "결제 대행(PG)사 구분자 (TOSS_PAYMENTS / KAKAO_PAY)", example = "TOSS_PAYMENTS")
    val pgProvider: String,
    @field:Positive
    @field:Schema(description = "실제 결제 금액 (원)", example = "10000")
    val amount: Long,
)
