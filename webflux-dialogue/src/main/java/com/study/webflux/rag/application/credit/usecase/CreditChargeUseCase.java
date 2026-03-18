package com.study.webflux.rag.application.credit.usecase;

import com.study.webflux.rag.domain.credit.model.CreditTransaction;
import com.study.webflux.rag.domain.credit.model.PaymentCharge;
import com.study.webflux.rag.domain.dialogue.model.UserId;
import com.study.webflux.rag.domain.mission.model.MissionId;
import reactor.core.publisher.Mono;

public interface CreditChargeUseCase {

	Mono<CreditTransaction> chargeByPayment(UserId userId, long amount, PaymentCharge source);

	Mono<CreditTransaction> grantSignupBonus(UserId userId);

	Mono<CreditTransaction> grantMissionReward(UserId userId, MissionId missionId, long amount,
		String missionType);

	Mono<Void> initializeIfAbsent(UserId userId);
}
