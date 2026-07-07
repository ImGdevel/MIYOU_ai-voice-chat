package com.miyou.app.api.common

import com.miyou.app.domain.auth.model.AuthenticatedUser
import org.springframework.http.HttpStatus
import org.springframework.web.server.ResponseStatusException
import reactor.core.publisher.Mono

object UserIdResolver {
    fun resolve(
        principal: AuthenticatedUser?,
        requestedUserId: String?,
    ): Mono<String> {
        val resolvedUserId = principal?.userId ?: requestedUserId
        return when {
            resolvedUserId.isNullOrBlank() -> {
                Mono.error(ResponseStatusException(HttpStatus.BAD_REQUEST, "userId is required"))
            }

            resolvedUserId.length > 128 -> {
                Mono.error(ResponseStatusException(HttpStatus.BAD_REQUEST, "userId cannot exceed 128 characters"))
            }

            else -> {
                Mono.just(resolvedUserId)
            }
        }
    }
}
