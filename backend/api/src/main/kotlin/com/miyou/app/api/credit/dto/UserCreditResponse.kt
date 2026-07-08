package com.miyou.app.api.credit.dto

import com.miyou.app.domain.credit.model.UserCredit
import io.swagger.v3.oas.annotations.media.Schema

@Schema(description = "사용자 크레딧 잔액 응답 DTO")
data class UserCreditResponse(
    @field:Schema(description = "사용자 고유 ID", example = "user-123")
    val userId: String,
    @field:Schema(description = "현재 보유 중인 크레딧 잔액", example = "450")
    val balance: Long,
) {
    companion object {
        fun from(credit: UserCredit): UserCreditResponse = UserCreditResponse(credit.userId, credit.balance)
    }
}
