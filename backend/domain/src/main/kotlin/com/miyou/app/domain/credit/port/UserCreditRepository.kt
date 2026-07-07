package com.miyou.app.domain.credit.port

import com.miyou.app.domain.credit.model.UserCredit
import reactor.core.publisher.Mono

interface UserCreditRepository {
    fun findByUserId(userId: String): Mono<UserCredit>

    fun save(userCredit: UserCredit): Mono<UserCredit>
}
