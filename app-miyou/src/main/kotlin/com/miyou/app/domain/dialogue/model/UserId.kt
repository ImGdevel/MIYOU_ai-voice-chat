package com.miyou.app.domain.dialogue.model

import java.util.UUID

data class UserId(
    val value: String,
) {
    init {
        require(value.isNotBlank()) { "userId cannot be null or blank" }
        require(value.length <= 128) { "userId too long" }
    }

    companion object {
        @JvmStatic
        fun of(value: String): UserId = UserId(value)

        @JvmStatic
        fun generate(): UserId = UserId(UUID.randomUUID().toString())
    }

    fun value(): String = value
}
