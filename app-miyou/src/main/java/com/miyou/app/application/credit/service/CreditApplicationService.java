package com.miyou.app.application.credit.service;

import lombok.extern.slf4j.Slf4j;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.miyou.app.application.credit.usecase.CreditChargeUseCase;
import com.miyou.app.application.credit.usecase.CreditDeductUseCase;
import com.miyou.app.application.credit.usecase.CreditQueryUseCase;
import com.miyou.app.domain.credit.exception.InsufficientCreditException;
import com.miyou.app.domain.credit.model.CreditTransaction;
import com.miyou.app.domain.credit.model.CreditTransactionType;
import com.miyou.app.domain.credit.model.UserCredit;
import com.miyou.app.domain.credit.model.ConversationDeduction;
import com.miyou.app.domain.credit.model.MissionReward;
import com.miyou.app.domain.credit.model.PaymentCharge;
import com.miyou.app.domain.credit.model.SignupBonus;
import com.miyou.app.application.credit.port.CreditTransactionRepository;
import com.miyou.app.domain.credit.port.UserCreditRepository;
import com.miyou.app.domain.dialogue.model.ConversationSessionId;
import com.miyou.app.domain.dialogue.model.UserId;
import com.miyou.app.domain.mission.model.MissionId;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Slf4j
@Service
public class CreditApplicationService
	implements CreditQueryUseCase, CreditChargeUseCase, CreditDeductUseCase {

	private final UserCreditRepository userCreditRepository;
	private final CreditTransactionRepository creditTransactionRepository;
	private final long conversationCost;
	private final long signupBonus;

	public CreditApplicationService(
		UserCreditRepository userCreditRepository,
		CreditTransactionRepository creditTransactionRepository,
		@Value("${credit.conversation-cost:100}") long conversationCost,
		@Value("${credit.signup-bonus:5000}") long signupBonus) {
		this.userCreditRepository = userCreditRepository;
		this.creditTransactionRepository = creditTransactionRepository;
		this.conversationCost = conversationCost;
		this.signupBonus = signupBonus;
	}

	@Override
	public Mono<UserCredit> getBalance(UserId userId) {
		return userCreditRepository.findByUserId(userId)
			.defaultIfEmpty(UserCredit.initialize(userId, 0L));
	}

	@Override
	public Flux<CreditTransaction> getTransactions(UserId userId, Pageable pageable) {
		return creditTransactionRepository.findByUserIdOrderByCreatedAtDesc(userId, pageable);
	}

	@Override
	@Transactional
	public Mono<CreditTransaction> deductForConversation(UserId userId,
		ConversationSessionId sessionId) {
		return userCreditRepository.findByUserId(userId)
			.switchIfEmpty(Mono.error(
				new InsufficientCreditException(userId, 0L, conversationCost)))
			.flatMap(credit -> {
				UserCredit updated = credit.deduct(conversationCost);
				CreditTransaction tx = CreditTransaction.of(
					userId,
					CreditTransactionType.DEDUCT,
					new ConversationDeduction(sessionId),
					conversationCost,
					credit.balance(),
					updated.balance(),
					sessionId.value());
				return userCreditRepository.save(updated)
					.flatMap(saved -> creditTransactionRepository.save(tx));
			});
	}

	@Override
	@Transactional
	public Mono<CreditTransaction> chargeByPayment(UserId userId, long amount, PaymentCharge source) {
		return userCreditRepository.findByUserId(userId)
			.defaultIfEmpty(UserCredit.initialize(userId, 0L))
			.flatMap(credit -> {
				UserCredit updated = credit.charge(amount);
				CreditTransaction tx = CreditTransaction.of(
					userId,
					CreditTransactionType.CHARGE,
					source,
					amount,
					credit.balance(),
					updated.balance(),
					source.paymentId());
				return userCreditRepository.save(updated)
					.flatMap(saved -> creditTransactionRepository.save(tx));
			});
	}

	@Override
	@Transactional
	public Mono<CreditTransaction> grantSignupBonus(UserId userId) {
		UserCredit initial = UserCredit.initialize(userId, signupBonus);
		CreditTransaction tx = CreditTransaction.of(
			userId,
			CreditTransactionType.CHARGE,
			new SignupBonus(),
			signupBonus,
			0L,
			signupBonus);
		return userCreditRepository.save(initial)
			.flatMap(saved -> creditTransactionRepository.save(tx));
	}

	@Override
	@Transactional
	public Mono<CreditTransaction> grantMissionReward(UserId userId, MissionId missionId, long amount,
		String missionType) {
		return userCreditRepository.findByUserId(userId)
			.defaultIfEmpty(UserCredit.initialize(userId, 0L))
			.flatMap(credit -> {
				UserCredit updated = credit.charge(amount);
				CreditTransaction tx = CreditTransaction.of(
					userId,
					CreditTransactionType.CHARGE,
					new MissionReward(missionId, missionType),
					amount,
					credit.balance(),
					updated.balance(),
					missionId.value());
				return userCreditRepository.save(updated)
					.flatMap(saved -> creditTransactionRepository.save(tx));
			});
	}

	@Override
	@Transactional
	public Mono<Void> initializeIfAbsent(UserId userId) {
		return userCreditRepository.findByUserId(userId)
			.hasElement()
			.flatMap(exists -> {
				if (exists) {
					return Mono.empty();
				}
				return grantSignupBonus(userId);
			})
			.then()
			.onErrorResume(DuplicateKeyException.class, e -> {
				log.debug("Signup bonus already granted for userId={} (race condition handled)",
					userId.value());
				return Mono.empty();
			});
	}
}
