package com.miyou.app.application.memory.service

import com.miyou.app.application.memory.policy.MemoryCuratorPolicy
import com.miyou.app.domain.memory.model.Memory
import com.miyou.app.domain.memory.port.VectorMemoryPort
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.stereotype.Service
import reactor.core.publisher.Mono
import java.time.Instant

/**
 * 메모리 큐레이터.
 *
 * 저장된 메모리의 importance를 시간 경과에 따라 감쇠시키고, 임계값 이하로 떨어지거나
 * 오래 미접근된 메모리를 소프트 아카이브(archivedAt 마킹, 물리 삭제 없음) 처리한다.
 */
@Service
class MemoryCuratorService(
    private val vectorMemoryPort: VectorMemoryPort,
    private val policy: MemoryCuratorPolicy,
) {
    private val logger = KotlinLogging.logger {}

    fun runDecayAndArchive(): Mono<Void> =
        vectorMemoryPort
            .findAllActive(BATCH_SIZE)
            .map(this::applyPolicy)
            .flatMap(vectorMemoryPort::applyDecayAndArchive)
            .doOnComplete { logger.info { "메모리 큐레이터 배치 완료" } }
            .doOnError { error -> logger.error(error) { "메모리 큐레이터 배치 실패" } }
            .then()

    private fun applyPolicy(memory: Memory): Memory {
        val now = Instant.now()
        val decayed =
            memory.decayImportance(
                now,
                policy.decayRateHigh,
                policy.decayRateLow,
                policy.decayExemptThreshold,
            )
        return if (decayed.shouldArchive(now, policy.archiveImportanceThreshold, policy.archiveIdleDays)) {
            decayed.archive(now)
        } else {
            decayed
        }
    }

    private companion object {
        const val BATCH_SIZE = 500
    }
}
