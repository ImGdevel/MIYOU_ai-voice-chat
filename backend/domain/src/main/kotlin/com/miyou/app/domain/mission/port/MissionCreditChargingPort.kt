package com.miyou.app.domain.mission.port

import com.miyou.app.domain.mission.model.MissionId
import reactor.core.publisher.Mono

interface MissionCreditChargingPort {
    fun grantReward(command: CreditRewardCommand): Mono<CreditRewardResult>
}

data class CreditRewardCommand(
    val userId: String,
    val missionId: MissionId,
    val amount: Long,
    val missionType: String,
)

data class CreditRewardResult(
    val transactionId: String,
)
