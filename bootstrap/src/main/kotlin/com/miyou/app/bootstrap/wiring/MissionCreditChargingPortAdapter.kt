package com.miyou.app.bootstrap.wiring

import com.miyou.app.application.credit.usecase.CreditChargeUseCase
import com.miyou.app.domain.mission.port.CreditChargingPort
import com.miyou.app.domain.mission.port.CreditRewardCommand
import com.miyou.app.domain.mission.port.CreditRewardResult
import org.springframework.stereotype.Component
import reactor.core.publisher.Mono

@Component
class MissionCreditChargingPortAdapter(
    private val creditChargeUseCase: CreditChargeUseCase,
) : CreditChargingPort {
    override fun grantReward(command: CreditRewardCommand): Mono<CreditRewardResult> =
        creditChargeUseCase
            .grantMissionReward(
                command.userId,
                command.missionId,
                command.amount,
                command.missionType,
            ).map { tx -> CreditRewardResult(tx.transactionId.value) }
}
