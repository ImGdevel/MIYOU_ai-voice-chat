package com.study.webflux.rag.application.memory.service;

import java.time.Instant;
import java.util.List;

import com.study.webflux.rag.domain.dialogue.model.UserId;
import com.study.webflux.rag.domain.memory.model.Memory;
import com.study.webflux.rag.domain.memory.model.MemoryEmbedding;
import com.study.webflux.rag.domain.memory.model.MemoryType;
import com.study.webflux.rag.domain.memory.port.EmbeddingPort;
import com.study.webflux.rag.domain.memory.port.VectorMemoryPort;
import com.study.webflux.rag.infrastructure.memory.adapter.MemoryExtractionConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentMatchers;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MemoryRetrievalServiceTest {

	@Mock
	private EmbeddingPort embeddingPort;

	@Mock
	private VectorMemoryPort vectorMemoryPort;

	private MemoryRetrievalService service;

	@BeforeEach
	void setUp() {
		MemoryExtractionConfig config = new MemoryExtractionConfig("gpt-4o-mini", 5, 0.05f, 0.3f);
		service = new MemoryRetrievalService(embeddingPort, vectorMemoryPort, config);
	}

	@Test
	@DisplayName("메모리 검색 시 랭킹, 제한, 접근 메트릭 업데이트를 수행한다")
	void retrieveMemories_shouldRankLimitAndUpdateAccessMetrics() {
		UserId userId = UserId.of("user-1");
		Instant now = Instant.now();
		Memory top = new Memory("m-top",
			userId,
			MemoryType.EXPERIENTIAL,
			"사용자는 러닝을 좋아한다",
			0.95f,
			now.minusSeconds(100),
			now,
			3);
		Memory second = new Memory("m-second",
			userId,
			MemoryType.FACTUAL,
			"사용자는 개발자다",
			0.70f,
			now.minusSeconds(100),
			now,
			2);
		Memory dropped = new Memory("m-dropped",
			userId,
			MemoryType.FACTUAL,
			"사용자는 고양이를 키운다",
			0.10f,
			now.minusSeconds(100),
			now,
			1);

		when(embeddingPort.embed("query")).thenReturn(
			Mono.just(MemoryEmbedding.of("query", List.of(0.1f, 0.2f))));
		when(vectorMemoryPort.search(userId,
			List.of(0.1f, 0.2f),
			List.of(MemoryType.EXPERIENTIAL, MemoryType.FACTUAL),
			0.3f,
			4)).thenReturn(Flux.just(top, second, dropped));
		when(vectorMemoryPort.updateImportance(ArgumentMatchers.anyString(),
			ArgumentMatchers.anyFloat(),
			ArgumentMatchers.any(),
			ArgumentMatchers.anyInt())).thenReturn(Mono.empty());

		StepVerifier.create(service.retrieveMemories(userId, "query", 2)).assertNext(result -> {
			assertThat(result.experientialMemories()).hasSize(1);
			assertThat(result.experientialMemories().get(0).id()).isEqualTo("m-top");
			assertThat(result.factualMemories()).hasSize(1);
			assertThat(result.factualMemories().get(0).id()).isEqualTo("m-second");
		}).verifyComplete();

		verify(vectorMemoryPort).updateImportance(ArgumentMatchers.eq("m-top"),
			ArgumentMatchers.anyFloat(),
			ArgumentMatchers.any(),
			ArgumentMatchers.eq(4));
		verify(vectorMemoryPort).updateImportance(ArgumentMatchers.eq("m-second"),
			ArgumentMatchers.anyFloat(),
			ArgumentMatchers.any(),
			ArgumentMatchers.eq(3));
		verify(vectorMemoryPort, never()).updateImportance(ArgumentMatchers.eq("m-dropped"),
			ArgumentMatchers.anyFloat(),
			ArgumentMatchers.any(),
			ArgumentMatchers.anyInt());
	}

	@Test
	@DisplayName("검색 결과가 없으면 빈 결과를 반환한다")
	void retrieveMemories_shouldReturnEmptyWithoutUpdateWhenSearchIsEmpty() {
		UserId userId = UserId.of("user-1");
		when(embeddingPort.embed("query")).thenReturn(
			Mono.just(MemoryEmbedding.of("query", List.of(0.1f, 0.2f))));
		when(vectorMemoryPort.search(ArgumentMatchers.eq(userId),
			ArgumentMatchers.anyList(),
			ArgumentMatchers.anyList(),
			ArgumentMatchers.anyFloat(),
			ArgumentMatchers.anyInt())).thenReturn(Flux.empty());

		StepVerifier.create(service.retrieveMemories(userId, "query", 3)).assertNext(result -> {
			assertThat(result.isEmpty()).isTrue();
		}).verifyComplete();

		verify(vectorMemoryPort, never()).updateImportance(ArgumentMatchers.anyString(),
			ArgumentMatchers.anyFloat(),
			ArgumentMatchers.any(),
			ArgumentMatchers.anyInt());
	}
}
