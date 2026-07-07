package com.miyou.app.application.memory.service

import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Service
import reactor.core.publisher.Mono

@Service
class MemoryCuratorScheduler(
    private val curatorService: MemoryCuratorService,
) {
    private val logger = KotlinLogging.logger {}

    @Scheduled(cron = "0 0 3 * * *")
    fun run() {
        curatorService
            .runDecayAndArchive()
            .doOnError { error -> logger.error(error) { "메모리 큐레이터 스케줄 실행 실패" } }
            .onErrorResume { Mono.empty() }
            .subscribe()
    }
}
