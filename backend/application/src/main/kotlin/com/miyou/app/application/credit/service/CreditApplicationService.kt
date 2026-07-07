package com.miyou.app.application.credit.service

import com.miyou.app.application.credit.port.CreditTransactionRepository
import com.miyou.app.application.credit.usecase.CreditChargeUseCase
import com.miyou.app.application.credit.usecase.CreditDeductUseCase
import com.miyou.app.application.credit.usecase.CreditQueryUseCase
import com.miyou.app.domain.credit.exception.InsufficientCreditException
import com.miyou.app.domain.credit.exception.UserCreditNotFoundException
import com.miyou.app.domain.credit.model.ConversationDeduction
import com.miyou.app.domain.credit.model.CreditTransaction
import com.miyou.app.domain.credit.model.CreditTransactionType
import com.miyou.app.domain.credit.model.MissionReward
import com.miyou.app.domain.credit.model.PaymentCharge
import com.miyou.app.domain.credit.model.SignupBonus
import com.miyou.app.domain.credit.model.UserCredit
import com.miyou.app.domain.credit.port.UserCreditRepository
import com.miyou.app.domain.mission.model.MissionId
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.beans.factory.annotation.Value
import org.springframework.dao.DuplicateKeyException
import org.springframework.data.domain.Pageable
import org.springframework.stereotype.Service
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono

/**
 * 크레딧 관리 서비스.
 *
 * 잔액 조회 → 차감/환불/충전/보너스 지급 관리.
 * 대화, 결제, 미션 보상 등 모든 크레딧 거래 처리.
 */
@Service
class CreditApplicationService(
    private val userCreditRepository: UserCreditRepository,
    private val creditTransactionRepository: CreditTransactionRepository,
    @Value("\${credit.conversation-cost:100}") private val conversationCost: Long,
    @Value("\${credit.signup-bonus:5000}") private val signupBonus: Long,
) : CreditQueryUseCase,
    CreditChargeUseCase,
    CreditDeductUseCase {
    private val log = KotlinLogging.logger {}

    /**
     * 사용자 크레딧 잔액 조회.
     *
     * @param userId 사용자 ID
     * @return 크레딧 잔액 (없으면 0 초기화)
     */
    override fun getBalance(userId: String): Mono<UserCredit> =
        userCreditRepository
            .findByUserId(userId)
            .defaultIfEmpty(UserCredit.initialize(userId, 0L))

    /**
     * 사용자 거래 내역 조회 (페이징).
     *
     * @param userId 사용자 ID
     * @param pageable 페이징 정보
     * @return 거래 내역 (최신순)
     */
    override fun getTransactions(
        userId: String,
        pageable: Pageable,
    ): Flux<CreditTransaction> = creditTransactionRepository.findByUserIdOrderByCreatedAtDesc(userId, pageable)

    /**
     * 크레딧 잔액을 갱신하고 거래 기록을 남기는 공통 처리.
     *
     * 잔액 조회(또는 부재 시 처리) → 잔액 변경 → [UserCredit]과 [CreditTransaction] 저장까지의
     * 반복되는 흐름을 하나로 묶는다. 차감/환불/충전/보상 지급이 모두 이 흐름을 공유한다.
     *
     * @param loadCredit 대상 사용자의 [UserCredit] 조회 (없을 때의 동작은 호출부가 결정)
     * @param applyBalance 조회된 잔액에 적용할 변경(차감/충전)
     * @param buildTransaction 변경 전/후 잔액을 받아 거래 기록을 생성
     * @return 저장된 거래 기록
     */
    private fun performCreditOperation(
        loadCredit: () -> Mono<UserCredit>,
        applyBalance: (UserCredit) -> UserCredit,
        buildTransaction: (balanceBefore: Long, balanceAfter: Long) -> CreditTransaction,
    ): Mono<CreditTransaction> =
        loadCredit()
            .flatMap { credit ->
                val updated = applyBalance(credit)
                val tx = buildTransaction(credit.balance, updated.balance)
                userCreditRepository
                    .save(updated)
                    .flatMap { creditTransactionRepository.save(tx) }
            }

    /**
     * 대화 크레딧 차감.
     * 사용자 잔액 검증 후 차감.
     *
     * @param userId 사용자 ID
     * @param sessionId 대화 세션 ID
     * @return 차감 거래 기록
     * @throws InsufficientCreditException 잔액 부족 시
     */
    override fun deductForConversation(
        userId: String,
        sessionId: String,
    ): Mono<CreditTransaction> =
        performCreditOperation(
            loadCredit = {
                userCreditRepository
                    .findByUserId(userId)
                    .switchIfEmpty(
                        Mono.error(InsufficientCreditException(userId, 0L, conversationCost)),
                    )
            },
            applyBalance = { it.deduct(conversationCost) },
            buildTransaction = { before, after ->
                CreditTransaction.of(
                    userId,
                    CreditTransactionType.DEDUCT,
                    ConversationDeduction(sessionId),
                    conversationCost,
                    before,
                    after,
                    sessionId,
                )
            },
        )

    /**
     * 대화 크레딧 환불.
     * 서비스 오류로 대화가 중단된 경우 사전차감분 반환.
     *
     * @param userId 사용자 ID
     * @param sessionId 대화 세션 ID
     * @return 환불 거래 기록
     */
    override fun refundForConversation(
        userId: String,
        sessionId: String,
    ): Mono<CreditTransaction> =
        performCreditOperation(
            loadCredit = {
                userCreditRepository
                    .findByUserId(userId)
                    .switchIfEmpty(
                        Mono.error(UserCreditNotFoundException(userId)),
                    )
            },
            applyBalance = { it.charge(conversationCost) },
            buildTransaction = { before, after ->
                CreditTransaction.of(
                    userId,
                    CreditTransactionType.REFUND,
                    ConversationDeduction(sessionId),
                    conversationCost,
                    before,
                    after,
                    sessionId,
                )
            },
        )

    /**
     * 결제를 통한 크레딧 충전.
     *
     * @param userId 사용자 ID
     * @param amount 충전액
     * @param source 결제 정보
     * @return 충전 거래 기록
     */
    override fun chargeByPayment(
        userId: String,
        amount: Long,
        source: PaymentCharge,
    ): Mono<CreditTransaction> =
        performCreditOperation(
            loadCredit = {
                userCreditRepository
                    .findByUserId(userId)
                    .defaultIfEmpty(UserCredit.initialize(userId, 0L))
            },
            applyBalance = { it.charge(amount) },
            buildTransaction = { before, after ->
                CreditTransaction.of(
                    userId,
                    CreditTransactionType.CHARGE,
                    source,
                    amount,
                    before,
                    after,
                    source.paymentId,
                )
            },
        )

    /**
     * 신규 사용자 가입 보너스 지급.
     *
     * @param userId 사용자 ID
     * @return 보너스 지급 거래 기록
     */
    override fun grantSignupBonus(userId: String): Mono<CreditTransaction> {
        val initial = UserCredit.initialize(userId, signupBonus)
        val tx =
            CreditTransaction.of(
                userId,
                CreditTransactionType.CHARGE,
                SignupBonus,
                signupBonus,
                0L,
                signupBonus,
                userId,
            )
        return userCreditRepository
            .save(initial)
            .flatMap { creditTransactionRepository.save(tx) }
    }

    /**
     * 미션 완료 보상 지급.
     *
     * @param userId 사용자 ID
     * @param missionId 미션 ID
     * @param amount 보상 크레딧
     * @param missionType 미션 유형
     * @return 보상 지급 거래 기록
     */
    override fun grantMissionReward(
        userId: String,
        missionId: MissionId,
        amount: Long,
        missionType: String,
    ): Mono<CreditTransaction> =
        performCreditOperation(
            loadCredit = {
                userCreditRepository
                    .findByUserId(userId)
                    .defaultIfEmpty(UserCredit.initialize(userId, 0L))
            },
            applyBalance = { it.charge(amount) },
            buildTransaction = { before, after ->
                CreditTransaction.of(
                    userId,
                    CreditTransactionType.CHARGE,
                    MissionReward(missionId.value, missionType),
                    amount,
                    before,
                    after,
                    missionId.value,
                )
            },
        )

    /**
     * 사용자 크레딧 초기화 (없을 경우만).
     * 가입 보너스로 크레딧 레코드 생성.
     * Race condition 발생 시 무시.
     *
     * @param userId 사용자 ID
     * @return 초기화 완료
     */
    override fun initializeIfAbsent(userId: String): Mono<Void> =
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
                log.debug { "Signup bonus already granted for userId=$userId (race condition handled)" }
                Mono.empty()
            }
}
