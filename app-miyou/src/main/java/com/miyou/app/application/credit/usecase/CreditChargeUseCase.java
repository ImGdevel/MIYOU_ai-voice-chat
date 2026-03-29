package com.miyou.app.application.credit.usecase;

import com.miyou.app.domain.credit.model.CreditTransaction;
import com.miyou.app.domain.credit.model.PaymentCharge;
import com.miyou.app.domain.dialogue.model.UserId;
import com.miyou.app.domain.mission.model.MissionId;
import reactor.core.publisher.Mono;

public interface CreditChargeUseCase {

	Mono<CreditTransaction> chargeByPayment(UserId userId, long amount, PaymentCharge source);

	Mono<CreditTransaction> grantSignupBonus(UserId userId);

	Mono<CreditTransaction> grantMissionReward(UserId userId, MissionId missionId, long amount,
		String missionType);

	Mono<Void> initializeIfAbsent(UserId userId);
}
