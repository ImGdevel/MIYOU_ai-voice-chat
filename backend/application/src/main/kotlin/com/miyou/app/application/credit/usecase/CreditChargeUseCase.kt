package com.miyou.app.application.credit.usecase

import com.miyou.app.domain.credit.model.CreditTransaction
import com.miyou.app.domain.credit.model.PaymentCharge
import com.miyou.app.domain.mission.model.MissionId
import reactor.core.publisher.Mono

interface CreditChargeUseCase {
    fun chargeByPayment(
        userId: String,
        amount: Long,
        source: PaymentCharge,
    ): Mono<CreditTransaction>

    fun grantSignupBonus(userId: String): Mono<CreditTransaction>

    fun grantMissionReward(
        userId: String,
        missionId: MissionId,
        amount: Long,
        missionType: String,
    ): Mono<CreditTransaction>

    fun initializeIfAbsent(userId: String): Mono<Void>
}
