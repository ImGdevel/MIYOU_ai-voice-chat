package com.miyou.app.application.credit.service

import com.miyou.app.application.credit.port.CreditTransactionRepository
import com.miyou.app.application.credit.usecase.CreditChargeUseCase
import com.miyou.app.application.credit.usecase.CreditDeductUseCase
import com.miyou.app.application.credit.usecase.CreditQueryUseCase
import com.miyou.app.domain.credit.exception.InsufficientCreditException
import com.miyou.app.domain.credit.model.ConversationDeduction
import com.miyou.app.domain.credit.model.CreditTransaction
import com.miyou.app.domain.credit.model.CreditTransactionType
import com.miyou.app.domain.credit.model.MissionReward
import com.miyou.app.domain.credit.model.PaymentCharge
import com.miyou.app.domain.credit.model.SignupBonus
import com.miyou.app.domain.credit.model.UserCredit
import com.miyou.app.domain.credit.port.UserCreditRepository
import com.miyou.app.domain.dialogue.model.ConversationSessionId
import com.miyou.app.domain.dialogue.model.UserId
import com.miyou.app.domain.mission.model.MissionId
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.dao.DuplicateKeyException
import org.springframework.data.domain.Pageable
import org.springframework.stereotype.Service
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono

@Service
class CreditApplicationService(
    private val userCreditRepository: UserCreditRepository,
    private val creditTransactionRepository: CreditTransactionRepository,
    @Value("\${credit.conversation-cost:100}") private val conversationCost: Long,
    @Value("\${credit.signup-bonus:5000}") private val signupBonus: Long,
) : CreditQueryUseCase,
    CreditChargeUseCase,
    CreditDeductUseCase {
    private val log = LoggerFactory.getLogger(javaClass)

    override fun getBalance(userId: UserId): Mono<UserCredit> =
        userCreditRepository
            .findByUserId(userId)
            .defaultIfEmpty(UserCredit.initialize(userId, 0L))

    override fun getTransactions(
        userId: UserId,
        pageable: Pageable,
    ): Flux<CreditTransaction> = creditTransactionRepository.findByUserIdOrderByCreatedAtDesc(userId, pageable)

    override fun deductForConversation(
        userId: UserId,
        sessionId: ConversationSessionId,
    ): Mono<CreditTransaction> =
        userCreditRepository
            .findByUserId(userId)
            .switchIfEmpty(
                Mono.error(
                    InsufficientCreditException(userId, 0L, conversationCost),
                ),
            ).flatMap { credit ->
                val updated = credit.deduct(conversationCost)
                val tx =
                    CreditTransaction.of(
                        userId,
                        CreditTransactionType.DEDUCT,
                        ConversationDeduction(sessionId),
                        conversationCost,
                        credit.balance,
                        updated.balance,
                        sessionId.value,
                    )
                userCreditRepository
                    .save(updated)
                    .flatMap { creditTransactionRepository.save(tx) }
            }

    override fun refundForConversation(
        userId: UserId,
        sessionId: ConversationSessionId,
    ): Mono<CreditTransaction> =
        userCreditRepository
            .findByUserId(userId)
            .switchIfEmpty(
                Mono.error(
                    IllegalStateException("Refund failed: User credit record not found for userId=${userId.value}"),
                ),
            ).flatMap { credit ->
                val updated = credit.charge(conversationCost)
                val tx =
                    CreditTransaction.of(
                        userId,
                        CreditTransactionType.REFUND,
                        ConversationDeduction(sessionId),
                        conversationCost,
                        credit.balance,
                        updated.balance,
                        sessionId.value,
                    )
                userCreditRepository
                    .save(updated)
                    .flatMap { creditTransactionRepository.save(tx) }
            }

    override fun chargeByPayment(
        userId: UserId,
        amount: Long,
        source: PaymentCharge,
    ): Mono<CreditTransaction> =
        userCreditRepository
            .findByUserId(userId)
            .defaultIfEmpty(UserCredit.initialize(userId, 0L))
            .flatMap { credit ->
                val updated = credit.charge(amount)
                val tx =
                    CreditTransaction.of(
                        userId,
                        CreditTransactionType.CHARGE,
                        source,
                        amount,
                        credit.balance,
                        updated.balance,
                        source.paymentId,
                    )
                userCreditRepository
                    .save(updated)
                    .flatMap { creditTransactionRepository.save(tx) }
            }

    override fun grantSignupBonus(userId: UserId): Mono<CreditTransaction> {
        val initial = UserCredit.initialize(userId, signupBonus)
        val tx =
            CreditTransaction.of(
                userId,
                CreditTransactionType.CHARGE,
                SignupBonus(),
                signupBonus,
                0L,
                signupBonus,
                userId.value,
            )
        return userCreditRepository
            .save(initial)
            .flatMap { creditTransactionRepository.save(tx) }
    }

    override fun grantMissionReward(
        userId: UserId,
        missionId: MissionId,
        amount: Long,
        missionType: String,
    ): Mono<CreditTransaction> =
        userCreditRepository
            .findByUserId(userId)
            .defaultIfEmpty(UserCredit.initialize(userId, 0L))
            .flatMap { credit ->
                val updated = credit.charge(amount)
                val tx =
                    CreditTransaction.of(
                        userId,
                        CreditTransactionType.CHARGE,
                        MissionReward(missionId, missionType),
                        amount,
                        credit.balance,
                        updated.balance,
                        missionId.value,
                    )
                userCreditRepository
                    .save(updated)
                    .flatMap { creditTransactionRepository.save(tx) }
            }

    override fun initializeIfAbsent(userId: UserId): Mono<Void> =
        userCreditRepository
            .findByUserId(userId)
            .hasElement()
            .flatMap { exists ->
                if (exists) {
                    Mono.empty()
                } else {
                    grantSignupBonus(userId).then()
                }
            }.onErrorResume(DuplicateKeyException::class.java) { e ->
                log.debug(
                    "Signup bonus already granted for userId={} (race condition handled)",
                    userId.value,
                )
                Mono.empty()
            }
}
