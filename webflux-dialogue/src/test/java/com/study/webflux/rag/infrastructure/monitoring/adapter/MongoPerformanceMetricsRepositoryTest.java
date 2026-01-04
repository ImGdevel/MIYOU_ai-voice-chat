package com.study.webflux.rag.infrastructure.monitoring.adapter;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import org.springframework.data.domain.PageRequest;

import com.study.webflux.rag.domain.monitoring.entity.PerformanceMetricsEntity;
import com.study.webflux.rag.domain.monitoring.model.PerformanceMetrics;
import com.study.webflux.rag.infrastructure.monitoring.repository.SpringDataPerformanceMetricsRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MongoPerformanceMetricsRepositoryTest {

	@Mock
	private SpringDataPerformanceMetricsRepository springRepository;

	private MongoPerformanceMetricsRepository repository;

	@BeforeEach
	void setUp() {
		repository = new MongoPerformanceMetricsRepository(springRepository);
	}

	@Test
	@DisplayName("메트릭 저장 성공")
	void save_success() {
		Instant now = Instant.now();
		PerformanceMetrics metrics = createMetrics("pipeline-1", "COMPLETED", now, 1000L);
		PerformanceMetricsEntity entity = PerformanceMetricsEntity.fromDomain(metrics);

		when(springRepository.save(any(PerformanceMetricsEntity.class))).thenReturn(
			Mono.just(entity));

		StepVerifier.create(repository.save(metrics)).assertNext(result -> {
			assertThat(result.pipelineId()).isEqualTo("pipeline-1");
			assertThat(result.status()).isEqualTo("COMPLETED");
			assertThat(result.totalDurationMillis()).isEqualTo(1000L);
		}).verifyComplete();

		verify(springRepository).save(any(PerformanceMetricsEntity.class));
	}

	@Test
	@DisplayName("ID로 메트릭 조회 성공")
	void findById_success() {
		Instant now = Instant.now();
		PerformanceMetrics metrics = createMetrics("pipeline-123", "COMPLETED", now, 500L);
		PerformanceMetricsEntity entity = PerformanceMetricsEntity.fromDomain(metrics);

		when(springRepository.findById("pipeline-123")).thenReturn(Mono.just(entity));

		StepVerifier.create(repository.findById("pipeline-123")).assertNext(result -> {
			assertThat(result.pipelineId()).isEqualTo("pipeline-123");
			assertThat(result.totalDurationMillis()).isEqualTo(500L);
		}).verifyComplete();
	}

	@Test
	@DisplayName("ID로 메트릭 조회 실패 시 빈 Mono 반환")
	void findById_notFound() {
		when(springRepository.findById("non-existent")).thenReturn(Mono.empty());

		StepVerifier.create(repository.findById("non-existent")).verifyComplete();
	}

	@Test
	@DisplayName("시간 범위로 메트릭 조회")
	void findByTimeRange_success() {
		Instant start = Instant.parse("2025-01-01T00:00:00Z");
		Instant end = Instant.parse("2025-01-02T00:00:00Z");
		Instant time1 = Instant.parse("2025-01-01T10:00:00Z");
		Instant time2 = Instant.parse("2025-01-01T15:00:00Z");

		PerformanceMetrics metrics1 = createMetrics("p1", "COMPLETED", time1, 1000L);
		PerformanceMetrics metrics2 = createMetrics("p2", "COMPLETED", time2, 2000L);

		when(springRepository.findByStartedAtBetweenOrderByStartedAtDesc(start, end)).thenReturn(
			Flux.just(PerformanceMetricsEntity.fromDomain(metrics2),
				PerformanceMetricsEntity.fromDomain(metrics1)));

		StepVerifier.create(repository.findByTimeRange(start, end))
			.assertNext(result -> assertThat(result.pipelineId()).isEqualTo("p2"))
			.assertNext(result -> assertThat(result.pipelineId()).isEqualTo("p1"))
			.verifyComplete();
	}

	@Test
	@DisplayName("상태별 메트릭 조회 with limit")
	void findByStatus_success() {
		Instant now = Instant.now();
		PerformanceMetrics metrics1 = createMetrics("p1", "FAILED", now, 100L);
		PerformanceMetrics metrics2 = createMetrics("p2", "FAILED", now.plusSeconds(10), 200L);

		when(springRepository.findByStatusOrderByStartedAtDesc(anyString(),
			any(PageRequest.class))).thenReturn(
				Flux.just(PerformanceMetricsEntity.fromDomain(metrics2),
					PerformanceMetricsEntity.fromDomain(metrics1)));

		StepVerifier.create(repository.findByStatus("FAILED", 10))
			.assertNext(result -> assertThat(result.status()).isEqualTo("FAILED"))
			.assertNext(result -> assertThat(result.status()).isEqualTo("FAILED"))
			.verifyComplete();

		verify(springRepository).findByStatusOrderByStartedAtDesc("FAILED",
			PageRequest.of(0, 10));
	}

	@Test
	@DisplayName("느린 파이프라인 조회")
	void findSlowPipelines_success() {
		Instant now = Instant.now();
		PerformanceMetrics slow1 = createMetrics("slow-1", "COMPLETED", now, 5000L);
		PerformanceMetrics slow2 = createMetrics("slow-2", "COMPLETED", now.plusSeconds(5), 6000L);

		when(springRepository.findSlowPipelines(anyLong(), any(PageRequest.class))).thenReturn(
			Flux.just(PerformanceMetricsEntity.fromDomain(slow1),
				PerformanceMetricsEntity.fromDomain(slow2)));

		StepVerifier.create(repository.findSlowPipelines(3000L, 5))
			.assertNext(result -> assertThat(result.totalDurationMillis()).isEqualTo(5000L))
			.assertNext(result -> assertThat(result.totalDurationMillis()).isEqualTo(6000L))
			.verifyComplete();

		verify(springRepository).findSlowPipelines(3000L, PageRequest.of(0, 5));
	}

	@Test
	@DisplayName("최근 메트릭 조회")
	void findRecent_success() {
		Instant now = Instant.now();
		PerformanceMetrics recent1 = createMetrics("r1", "COMPLETED", now, 100L);
		PerformanceMetrics recent2 = createMetrics("r2", "COMPLETED", now.minusSeconds(10), 200L);

		when(springRepository.findAllByOrderByStartedAtDesc(any(PageRequest.class))).thenReturn(
			Flux.just(PerformanceMetricsEntity.fromDomain(recent1),
				PerformanceMetricsEntity.fromDomain(recent2)));

		StepVerifier.create(repository.findRecent(2))
			.assertNext(result -> assertThat(result.pipelineId()).isEqualTo("r1"))
			.assertNext(result -> assertThat(result.pipelineId()).isEqualTo("r2"))
			.verifyComplete();

		verify(springRepository).findAllByOrderByStartedAtDesc(PageRequest.of(0, 2));
	}

	@Test
	@DisplayName("limit이 0 이하일 때 1로 보정")
	void findByStatus_limitCorrection() {
		when(springRepository.findByStatusOrderByStartedAtDesc(anyString(),
			any(PageRequest.class))).thenReturn(Flux.empty());

		repository.findByStatus("COMPLETED", 0).blockLast();

		verify(springRepository).findByStatusOrderByStartedAtDesc("COMPLETED",
			PageRequest.of(0, 1));
	}

	@Test
	@DisplayName("느린 파이프라인 조회 시 limit 음수 보정")
	void findSlowPipelines_negativeLimit() {
		when(springRepository.findSlowPipelines(anyLong(), any(PageRequest.class))).thenReturn(
			Flux.empty());

		repository.findSlowPipelines(1000L, -5).blockLast();

		verify(springRepository).findSlowPipelines(1000L, PageRequest.of(0, 1));
	}

	@Test
	@DisplayName("Stage 정보가 포함된 메트릭 저장 및 조회")
	void save_withStages_success() {
		Instant now = Instant.now();
		PerformanceMetrics.StagePerformance stage1 = new PerformanceMetrics.StagePerformance(
			"RAG_RETRIEVAL", "COMPLETED", now, now.plusMillis(100), 100L, Map.of("topK", 5));
		PerformanceMetrics.StagePerformance stage2 = new PerformanceMetrics.StagePerformance(
			"LLM_GENERATION", "COMPLETED", now.plusMillis(100), now.plusMillis(900), 800L,
			Map.of("model", "gpt-4"));

		PerformanceMetrics metrics = new PerformanceMetrics("p-stages", "COMPLETED", now,
			now.plusMillis(900), 900L, 50L, 850L, List.of(stage1, stage2),
			Map.of("version", "1.0"));

		PerformanceMetricsEntity entity = PerformanceMetricsEntity.fromDomain(metrics);
		when(springRepository.save(any(PerformanceMetricsEntity.class))).thenReturn(
			Mono.just(entity));

		StepVerifier.create(repository.save(metrics)).assertNext(result -> {
			assertThat(result.stages()).hasSize(2);
			assertThat(result.stages().get(0).stageName()).isEqualTo("RAG_RETRIEVAL");
			assertThat(result.stages().get(1).stageName()).isEqualTo("LLM_GENERATION");
			assertThat(result.systemAttributes()).containsEntry("version", "1.0");
		}).verifyComplete();
	}

	@Test
	@DisplayName("빈 결과 조회")
	void findByTimeRange_empty() {
		Instant start = Instant.now();
		Instant end = start.plusSeconds(3600);

		when(springRepository.findByStartedAtBetweenOrderByStartedAtDesc(start, end)).thenReturn(
			Flux.empty());

		StepVerifier.create(repository.findByTimeRange(start, end)).verifyComplete();
	}

	@Test
	@DisplayName("저장 실패 시 에러 전파")
	void save_error_propagates() {
		PerformanceMetrics metrics = createMetrics("err-1", "FAILED", Instant.now(), 100L);

		when(springRepository.save(any(PerformanceMetricsEntity.class))).thenReturn(
			Mono.error(new RuntimeException("DB write error")));

		StepVerifier.create(repository.save(metrics))
			.expectErrorMatches(throwable -> throwable.getMessage().contains("DB write error"))
			.verify();
	}

	@Test
	@DisplayName("조회 실패 시 에러 전파")
	void findByStatus_error_propagates() {
		when(springRepository.findByStatusOrderByStartedAtDesc(anyString(),
			any(PageRequest.class))).thenReturn(
				Flux.error(new RuntimeException("Query timeout")));

		StepVerifier.create(repository.findByStatus("COMPLETED", 10))
			.expectErrorMatches(throwable -> throwable.getMessage().contains("Query timeout"))
			.verify();
	}

	private PerformanceMetrics createMetrics(String pipelineId,
		String status,
		Instant startedAt,
		long duration) {
		return new PerformanceMetrics(pipelineId, status, startedAt,
			startedAt.plusMillis(duration), duration, null, null, List.of(), Map.of());
	}
}
