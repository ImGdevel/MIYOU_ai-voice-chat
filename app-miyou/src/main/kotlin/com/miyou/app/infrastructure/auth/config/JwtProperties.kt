package com.miyou.app.infrastructure.auth.config

import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.stereotype.Component

@Component
@ConfigurationProperties(prefix = "jwt")
class JwtProperties {
    var secret: String = ""
    var accessTokenTtlMinutes: Long = 15
    var refreshTokenTtlDays: Long = 14
    var issuer: String = "miyou-app"
}
