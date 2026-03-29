package com.miyou.app.infrastructure.credit.adapter;

import lombok.RequiredArgsConstructor;

import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;

import com.miyou.app.application.credit.port.CreditTransactionRepository;
import com.miyou.app.domain.credit.model.CreditTransaction;
import com.miyou.app.domain.dialogue.model.UserId;
import com.miyou.app.infrastructure.credit.document.CreditTransactionDocument;
import com.miyou.app.infrastructure.credit.repository.CreditTransactionMongoRepository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Component
@RequiredArgsConstructor
public class CreditTransactionMongoAdapter implements CreditTransactionRepository {

	private final CreditTransactionMongoRepository mongoRepository;

	@Override
	public Mono<CreditTransaction> save(CreditTransaction transaction) {
		return mongoRepository.save(CreditTransactionDocument.fromDomain(transaction))
			.map(CreditTransactionDocument::toDomain);
	}

	@Override
	public Flux<CreditTransaction> findByUserIdOrderByCreatedAtDesc(UserId userId,
		Pageable pageable) {
		return mongoRepository
			.findByUserIdOrderByCreatedAtDesc(userId.value(), pageable)
			.map(CreditTransactionDocument::toDomain);
	}
}
