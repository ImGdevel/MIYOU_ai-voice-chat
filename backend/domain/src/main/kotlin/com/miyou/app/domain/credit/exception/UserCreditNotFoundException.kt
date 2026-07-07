package com.miyou.app.domain.credit.exception

class UserCreditNotFoundException(
    val userId: String,
) : RuntimeException("User credit record not found for userId=$userId")
