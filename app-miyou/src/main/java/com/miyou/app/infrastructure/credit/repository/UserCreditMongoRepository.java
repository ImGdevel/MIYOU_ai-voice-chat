package com.miyou.app.infrastructure.credit.repository;

import org.springframework.data.mongodb.repository.ReactiveMongoRepository;

import com.miyou.app.infrastructure.credit.document.UserCreditDocument;
import reactor.core.publisher.Mono;

public interface UserCreditMongoRepository
	extends
		ReactiveMongoRepository<UserCreditDocument, String> {

	Mono<UserCreditDocument> findByUserId(String userId);
}
