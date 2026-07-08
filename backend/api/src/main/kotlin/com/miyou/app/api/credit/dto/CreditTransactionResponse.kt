package com.miyou.app.api.credit.dto

import com.miyou.app.domain.credit.model.CreditTransaction
import io.swagger.v3.oas.annotations.media.Schema
import java.time.Instant

@Schema(description = "크레딧 거래 내역 응답 DTO")
data class CreditTransactionResponse(
    @field:Schema(description = "거래 내역 고유 ID", example = "tx_9876543210")
    val transactionId: String,
    @field:Schema(description = "대상 사용자 ID", example = "user-123")
    val userId: String,
    @field:Schema(description = "거래 유형 (CHARGE: 충전, USE: 차감, REFUND: 환불)", example = "CHARGE")
    val type: String,
    @field:Schema(
        description = "거래 출처 (PAYMENT: 실제 결제, DIALOGUE: RAG 대화 사용, MISSION: 일일 미션 보상, SYSTEM: 시스템 관리자 지급)",
        example = "PAYMENT"
    )
    val sourceType: String,
    @field:Schema(description = "변동 금액 (충전/환불 시 +, 차감 시 -)", example = "1000")
    val amount: Long,
    @field:Schema(description = "변동 전 크레딧 잔액", example = "5000")
    val balanceBefore: Long,
    @field:Schema(description = "변동 후 크레딧 잔액", example = "6000")
    val balanceAfter: Long,
    @field:Schema(description = "참조 ID (결제 ID, 세션 ID 등 비즈니스 연관 ID)", example = "pay_id_12345abcdef")
    val referenceId: String?,
    @field:Schema(description = "거래 발생 시각", example = "2026-07-08T16:30:00Z")
    val createdAt: Instant?,
) {
    companion object {
        fun from(tx: CreditTransaction): CreditTransactionResponse =
            CreditTransactionResponse(
                tx.transactionId.value,
                tx.userId,
                tx.type.name,
                tx.source.sourceType().name,
                tx.amount,
                tx.balanceBefore,
                tx.balanceAfter,
                tx.referenceId,
                tx.createdAt,
            )
    }
}
