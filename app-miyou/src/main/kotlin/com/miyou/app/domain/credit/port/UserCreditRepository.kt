package com.miyou.app.domain.credit.port

import com.miyou.app.domain.credit.model.UserCredit
import com.miyou.app.domain.dialogue.model.UserId
import reactor.core.publisher.Mono

interface UserCreditRepository {
    fun findByUserId(userId: UserId): Mono<UserCredit>

    fun save(userCredit: UserCredit): Mono<UserCredit>
}
