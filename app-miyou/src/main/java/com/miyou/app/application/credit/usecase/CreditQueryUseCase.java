package com.miyou.app.application.credit.usecase;

import org.springframework.data.domain.Pageable;

import com.miyou.app.domain.credit.model.CreditTransaction;
import com.miyou.app.domain.credit.model.UserCredit;
import com.miyou.app.domain.dialogue.model.UserId;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public interface CreditQueryUseCase {

	Mono<UserCredit> getBalance(UserId userId);

	Flux<CreditTransaction> getTransactions(UserId userId, Pageable pageable);
}
