package com.study.webflux.rag.application.credit.usecase;

import org.springframework.data.domain.Pageable;

import com.study.webflux.rag.domain.credit.model.CreditTransaction;
import com.study.webflux.rag.domain.credit.model.UserCredit;
import com.study.webflux.rag.domain.dialogue.model.UserId;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public interface CreditQueryUseCase {

	Mono<UserCredit> getBalance(UserId userId);

	Flux<CreditTransaction> getTransactions(UserId userId, Pageable pageable);
}
