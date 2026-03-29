package com.miyou.app.application.credit.port;

import org.springframework.data.domain.Pageable;

import com.miyou.app.domain.credit.model.CreditTransaction;
import com.miyou.app.domain.dialogue.model.UserId;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public interface CreditTransactionRepository {

	Mono<CreditTransaction> save(CreditTransaction transaction);

	Flux<CreditTransaction> findByUserIdOrderByCreatedAtDesc(UserId userId, Pageable pageable);
}
