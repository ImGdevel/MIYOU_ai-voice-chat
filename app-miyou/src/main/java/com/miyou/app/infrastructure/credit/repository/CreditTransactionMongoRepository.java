package com.miyou.app.infrastructure.credit.repository;

import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.ReactiveMongoRepository;

import com.miyou.app.infrastructure.credit.document.CreditTransactionDocument;
import reactor.core.publisher.Flux;

public interface CreditTransactionMongoRepository
	extends
		ReactiveMongoRepository<CreditTransactionDocument, String> {

	Flux<CreditTransactionDocument> findByUserIdOrderByCreatedAtDesc(String userId,
		Pageable pageable);
}
