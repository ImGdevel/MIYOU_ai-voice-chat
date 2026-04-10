package com.miyou.app.infrastructure.monitoring.adapter

import com.miyou.app.domain.monitoring.model.PerformanceMetrics
import com.miyou.app.infrastructure.monitoring.document.PerformanceMetricsDocument
import com.miyou.app.infrastructure.monitoring.repository.SpringDataPerformanceMetricsRepository
import com.miyou.app.support.anyLongValue
import com.miyou.app.support.anyStringValue
import com.miyou.app.support.anyValue
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
import org.mockito.junit.jupiter.MockitoExtension
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Sort
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import reactor.test.StepVerifier
import java.time.Instant

@ExtendWith(MockitoExtension::class)
class MongoPerformanceMetricsRepositoryTest {
    @org.mockito.Mock
    private lateinit var springRepository: SpringDataPerformanceMetricsRepository

    private lateinit var repository: MongoPerformanceMetricsRepository

    @BeforeEach
    fun setUp() {
        repository = MongoPerformanceMetricsRepository(springRepository)
    }

    @Test
    @DisplayName("저장 결과 문서를 다시 도메인 모델로 변환한다")
    fun save_mapsDocumentBackIntoDomainModel() {
        val now = Instant.now()
        val metrics = createMetrics("pipeline-1", "COMPLETED", now, 1000L)
        val document = PerformanceMetricsDocument.fromDomain(metrics)

        `when`(springRepository.save(anyValue())).thenReturn(Mono.just(document))

        StepVerifier
            .create(repository.save(metrics))
            .assertNext { result ->
                assertThat(result.pipelineId).isEqualTo("pipeline-1")
                assertThat(result.status).isEqualTo("COMPLETED")
                assertThat(result.totalDurationMillis).isEqualTo(1000L)
            }.verifyComplete()
    }

    @Test
    @DisplayName("ID로 조회하면 매핑된 도메인 객체를 반환한다")
    fun findById_returnsMappedDomainObject() {
        val now = Instant.now()
        val metrics = createMetrics("pipeline-123", "COMPLETED", now, 500L)

        `when`(springRepository.findById("pipeline-123"))
            .thenReturn(Mono.just(PerformanceMetricsDocument.fromDomain(metrics)))

        StepVerifier
            .create(repository.findById("pipeline-123"))
            .assertNext { result -> assertThat(result.pipelineId).isEqualTo("pipeline-123") }
            .verifyComplete()
    }

    @Test
    @DisplayName("상태 조회 시 요청한 페이지 크기로 위임한다")
    fun findByStatus_delegatesWithRequestedPageSize() {
        val now = Instant.now()
        val metrics1 = createMetrics("p1", "FAILED", now, 100L)
        val metrics2 = createMetrics("p2", "FAILED", now.plusSeconds(10), 200L)

        `when`(springRepository.findByStatusOrderByStartedAtDesc(anyStringValue(), anyValue()))
            .thenReturn(
                Flux.just(
                    PerformanceMetricsDocument.fromDomain(metrics2),
                    PerformanceMetricsDocument.fromDomain(metrics1),
                ),
            )

        StepVerifier
            .create(repository.findByStatus("FAILED", 10))
            .expectNextCount(2)
            .verifyComplete()

        verify(springRepository).findByStatusOrderByStartedAtDesc("FAILED", PageRequest.of(0, 10))
    }

    @Test
    @DisplayName("느린 파이프라인 조회는 총 소요 시간을 내림차순으로 정렬한다")
    fun findSlowPipelines_sortsByDurationDescending() {
        val now = Instant.now()
        val slow1 = createMetrics("slow-1", "COMPLETED", now, 5000L)
        val slow2 = createMetrics("slow-2", "COMPLETED", now.plusSeconds(5), 6000L)

        `when`(springRepository.findByTotalDurationMillisGreaterThanEqual(anyLongValue(), anyValue()))
            .thenReturn(
                Flux.just(
                    PerformanceMetricsDocument.fromDomain(slow2),
                    PerformanceMetricsDocument.fromDomain(slow1),
                ),
            )

        StepVerifier
            .create(repository.findSlowPipelines(3000L, 5))
            .assertNext { result -> assertThat(result.totalDurationMillis).isEqualTo(6000L) }
            .assertNext { result -> assertThat(result.totalDurationMillis).isEqualTo(5000L) }
            .verifyComplete()

        verify(springRepository).findByTotalDurationMillisGreaterThanEqual(
            3000L,
            PageRequest.of(0, 5, Sort.by(Sort.Direction.DESC, "totalDurationMillis")),
        )
    }

    private fun createMetrics(
        pipelineId: String,
        status: String,
        startedAt: Instant,
        totalDurationMillis: Long,
    ): PerformanceMetrics =
        PerformanceMetrics(
            pipelineId = pipelineId,
            status = status,
            startedAt = startedAt,
            finishedAt = startedAt.plusMillis(totalDurationMillis),
            totalDurationMillis = totalDurationMillis,
            firstResponseLatencyMillis = 50L,
            lastResponseLatencyMillis = totalDurationMillis - 50L,
            stages = emptyList(),
            systemAttributes = mapOf("version" to "1.0"),
        )
}
