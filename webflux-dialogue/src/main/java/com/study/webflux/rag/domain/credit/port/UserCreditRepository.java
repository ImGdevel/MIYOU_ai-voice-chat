package com.study.webflux.rag.domain.credit.port;

import com.study.webflux.rag.domain.credit.model.UserCredit;
import com.study.webflux.rag.domain.dialogue.model.UserId;
import reactor.core.publisher.Mono;

public interface UserCreditRepository {

	Mono<UserCredit> findByUserId(UserId userId);

	Mono<UserCredit> save(UserCredit userCredit);
}
