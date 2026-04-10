package com.miyou.app.infrastructure.monitoring.config

import io.micrometer.core.instrument.Gauge
import io.micrometer.core.instrument.MeterRegistry
import org.springframework.context.annotation.Configuration
import org.springframework.stereotype.Component
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

/**
 * 도메인 파이프라인 병목 분석을 위한 메트릭 구성을 담당합니다.
 */
@Configuration
class PipelineMetricsConfiguration

/**
 * Backpressure 메트릭을 담당합니다. (Sentence buffer / stage data size)
 */
@Component
class BackpressureMetrics(
    private val meterRegistry: MeterRegistry,
) {
    private val sentenceBufferSize = AtomicInteger(0)
    private val stageDataSizes = ConcurrentHashMap<String, AtomicInteger>()

    init {
        registerMetrics()
    }

    private fun registerMetrics() {
        Gauge
            .builder("pipeline.sentence.buffer.size", sentenceBufferSize) { it.get().toDouble() }
            .description("Current size of sentence buffer")
            .register(meterRegistry)
    }

    /**
     * Sentence buffer 크기를 갱신합니다.
     */
    fun updateSentenceBufferSize(size: Int) {
        sentenceBufferSize.set(size)
    }

    /**
     * Stage 데이터 크기를 갱신합니다.
     */
    fun recordStageDataSize(
        stage: String,
        dataType: String,
        sizeBytes: Int,
    ) {
        val key = "$stage:$dataType"
        stageDataSizes
            .computeIfAbsent(key) { _ ->
                val gauge = AtomicInteger(0)
                Gauge
                    .builder("pipeline.data.size.bytes", gauge) { it.get().toDouble() }
                    .tag("stage", stage)
                    .tag("data_type", dataType)
                    .description("Data size in bytes for pipeline stage")
                    .register(meterRegistry)
                gauge
            }.set(sizeBytes)
    }

    /**
     * 현재 Sentence buffer 크기를 반환합니다.
     */
    fun getSentenceBufferSize(): Int = sentenceBufferSize.get()
}
