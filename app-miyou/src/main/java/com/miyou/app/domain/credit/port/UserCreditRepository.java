package com.miyou.app.domain.credit.port;

import com.miyou.app.domain.credit.model.UserCredit;
import com.miyou.app.domain.dialogue.model.UserId;
import reactor.core.publisher.Mono;

public interface UserCreditRepository {

	Mono<UserCredit> findByUserId(UserId userId);

	Mono<UserCredit> save(UserCredit userCredit);
}
