package com.miyou.app.bootstrap.wiring

import com.miyou.app.application.credit.usecase.CreditDeductUseCase
import com.miyou.app.domain.dialogue.port.CreditDeductCommand
import com.miyou.app.domain.dialogue.port.CreditDeductResult
import com.miyou.app.domain.dialogue.port.CreditRefundCommand
import com.miyou.app.domain.dialogue.port.CreditRefundResult
import com.miyou.app.domain.dialogue.port.DialogueCreditChargingPort
import org.springframework.stereotype.Component
import reactor.core.publisher.Mono

@Component
class DialogueCreditChargingPortAdapter(
    private val creditDeductUseCase: CreditDeductUseCase,
) : DialogueCreditChargingPort {
    override fun deduct(command: CreditDeductCommand): Mono<CreditDeductResult> =
        creditDeductUseCase
            .deductForConversation(command.userId, command.sessionId)
            .map { tx -> CreditDeductResult(tx.transactionId.value) }

    override fun refund(command: CreditRefundCommand): Mono<CreditRefundResult> =
        creditDeductUseCase
            .refundForConversation(command.userId, command.sessionId)
            .map { tx -> CreditRefundResult(tx.transactionId.value) }
}
