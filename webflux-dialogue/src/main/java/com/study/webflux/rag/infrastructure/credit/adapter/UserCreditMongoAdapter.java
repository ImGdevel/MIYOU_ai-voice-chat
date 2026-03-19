package com.study.webflux.rag.infrastructure.credit.adapter;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Component;

import com.study.webflux.rag.domain.credit.model.UserCredit;
import com.study.webflux.rag.domain.credit.port.UserCreditRepository;
import com.study.webflux.rag.domain.dialogue.model.UserId;
import com.study.webflux.rag.infrastructure.credit.document.UserCreditDocument;
import com.study.webflux.rag.infrastructure.credit.repository.UserCreditMongoRepository;
import reactor.core.publisher.Mono;

@Component
@RequiredArgsConstructor
public class UserCreditMongoAdapter implements UserCreditRepository {

	private final UserCreditMongoRepository mongoRepository;

	@Override
	public Mono<UserCredit> findByUserId(UserId userId) {
		return mongoRepository.findByUserId(userId.value()).map(UserCreditDocument::toDomain);
	}

	@Override
	public Mono<UserCredit> save(UserCredit userCredit) {
		return mongoRepository.save(UserCreditDocument.fromDomain(userCredit))
			.map(UserCreditDocument::toDomain);
	}
}
