package com.miyou.app.infrastructure.inbound.web.monitoring

import org.springframework.http.HttpStatus
import org.springframework.http.server.reactive.ServerHttpResponse
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RestController
import reactor.core.publisher.Mono
import java.net.URI

@RestController
class DashboardController {
    @GetMapping("/dashboard")
    fun dashboard(response: ServerHttpResponse): Mono<Void> {
        response.statusCode = HttpStatus.TEMPORARY_REDIRECT
        response.headers.location = URI.create("/admin/monitoring/grafana/")
        return response.setComplete()
    }

    @GetMapping("/monitoring")
    fun monitoring(response: ServerHttpResponse): Mono<Void> {
        response.statusCode = HttpStatus.TEMPORARY_REDIRECT
        response.headers.location = URI.create("/admin/monitoring/grafana/")
        return response.setComplete()
    }
}
