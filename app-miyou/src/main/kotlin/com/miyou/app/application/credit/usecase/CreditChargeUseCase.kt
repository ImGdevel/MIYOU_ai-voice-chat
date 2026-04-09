package com.miyou.app.application.credit.usecase

import com.miyou.app.domain.credit.model.CreditTransaction
import com.miyou.app.domain.credit.model.PaymentCharge
import com.miyou.app.domain.dialogue.model.UserId
import com.miyou.app.domain.mission.model.MissionId
import reactor.core.publisher.Mono

interface CreditChargeUseCase {
    fun chargeByPayment(
        userId: UserId,
        amount: Long,
        source: PaymentCharge,
    ): Mono<CreditTransaction>

    fun grantSignupBonus(userId: UserId): Mono<CreditTransaction>

    fun grantMissionReward(
        userId: UserId,
        missionId: MissionId,
        amount: Long,
        missionType: String,
    ): Mono<CreditTransaction>

    fun initializeIfAbsent(userId: UserId): Mono<Void>
}
