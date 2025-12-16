package com.study.webflux.rag.infrastructure.adapter.vectordb;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.VectorStore;

import com.google.common.util.concurrent.Futures;
import com.study.webflux.rag.domain.model.memory.Memory;
import com.study.webflux.rag.domain.model.memory.MemoryType;
import com.study.webflux.rag.infrastructure.config.properties.RagDialogueProperties;
import io.qdrant.client.QdrantClient;
import io.qdrant.client.grpc.JsonWithInt;
import io.qdrant.client.grpc.Points;
import io.qdrant.client.grpc.Points.ScoredPoint;
import io.qdrant.client.grpc.Points.SearchPoints;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.test.StepVerifier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SpringAiVectorDbAdapterTest {

	@Mock
	private VectorStore vectorStore;

	@Mock
	private QdrantClient qdrantClient;

	@Mock
	private RagDialogueProperties properties;

	private SpringAiVectorDbAdapter vectorDbAdapter;

	@BeforeEach
	void setUp() {
		RagDialogueProperties.Qdrant qdrantConfig = new RagDialogueProperties.Qdrant();
		qdrantConfig.setCollectionName("user_memories");
		when(properties.getQdrant()).thenReturn(qdrantConfig);

		vectorDbAdapter = new SpringAiVectorDbAdapter(vectorStore, qdrantClient, properties);
	}

	@Test
	@DisplayName("메모리를 벡터 DB에 저장한다")
	void upsert_success() {
		Memory memory = new Memory(null, MemoryType.EXPERIENTIAL, "테스트 메모리", 0.8f, Instant.now(),
			Instant.now(), 1);
		List<Float> embedding = List.of(0.1f, 0.2f, 0.3f);

		StepVerifier.create(vectorDbAdapter.upsert(memory, embedding)).assertNext(result -> {
			assertThat(result).isNotNull();
			assertThat(result.id()).isNotNull();
			assertThat(result.content()).isEqualTo("테스트 메모리");
			assertThat(result.type()).isEqualTo(MemoryType.EXPERIENTIAL);
		}).verifyComplete();

		verify(vectorStore).add(anyList());
	}

	@Test
	@DisplayName("ID가 있는 메모리를 저장할 때 ID를 유지한다")
	void upsert_withExistingId_preservesId() {
		String existingId = "existing-id-123";
		Memory memory = new Memory(existingId, MemoryType.FACTUAL, "기존 메모리", 0.5f, null, null,
			null);
		List<Float> embedding = List.of(0.1f, 0.2f);

		StepVerifier.create(vectorDbAdapter.upsert(memory, embedding)).assertNext(result -> {
			assertThat(result.id()).isEqualTo(existingId);
		}).verifyComplete();
	}

	@Test
	@DisplayName("메모리 저장 시 메타데이터를 올바르게 설정한다")
	void upsert_setsMetadataCorrectly() {
		Instant createdAt = Instant.parse("2025-01-01T00:00:00Z");
		Instant lastAccessedAt = Instant.parse("2025-01-02T00:00:00Z");

		Memory memory = new Memory(null, MemoryType.EXPERIENTIAL, "메타데이터 테스트", 0.9f, createdAt,
			lastAccessedAt, 5);
		List<Float> embedding = List.of(0.1f);

		ArgumentCaptor<List<Document>> captor = ArgumentCaptor.forClass(List.class);

		StepVerifier.create(vectorDbAdapter.upsert(memory, embedding))
			.assertNext(result -> assertThat(result).isNotNull()).verifyComplete();

		verify(vectorStore).add(captor.capture());
		Document capturedDoc = captor.getValue().get(0);
		Map<String, Object> metadata = capturedDoc.getMetadata();

		assertThat(metadata.get("type")).isEqualTo("EXPERIENTIAL");
		assertThat(metadata.get("importance")).isEqualTo(0.9f);
		assertThat(metadata.get("createdAt")).isEqualTo(createdAt.toEpochMilli());
		assertThat(metadata.get("lastAccessedAt")).isEqualTo(lastAccessedAt.toEpochMilli());
		assertThat(metadata.get("accessCount")).isEqualTo(5);
	}

	@Test
	@DisplayName("타입과 중요도 필터로 메모리를 검색한다")
	void search_withFilters_success() throws Exception {
		List<Float> queryEmbedding = List.of(0.1f, 0.2f, 0.3f);
		List<MemoryType> types = List.of(MemoryType.EXPERIENTIAL, MemoryType.FACTUAL);
		float importanceThreshold = 0.5f;
		int topK = 5;

		ScoredPoint mockPoint = ScoredPoint.newBuilder()
			.setId(Points.PointId.newBuilder().setUuid("doc-1").build())
			.putPayload("content", JsonWithInt.Value.newBuilder().setStringValue("테스트 내용").build())
			.putPayload("type",
				JsonWithInt.Value.newBuilder().setStringValue("EXPERIENTIAL").build())
			.putPayload("importance", JsonWithInt.Value.newBuilder().setDoubleValue(0.8).build())
			.build();

		when(qdrantClient.searchAsync(any(SearchPoints.class)))
			.thenReturn(Futures.immediateFuture(List.of(mockPoint)));

		StepVerifier
			.create(vectorDbAdapter.search(queryEmbedding, types, importanceThreshold, topK))
			.assertNext(result -> {
				assertThat(result).isNotNull();
				assertThat(result.id()).isEqualTo("doc-1");
				assertThat(result.content()).isEqualTo("테스트 내용");
				assertThat(result.type()).isEqualTo(MemoryType.EXPERIENTIAL);
				assertThat(result.importance()).isEqualTo(0.8f);
			}).verifyComplete();
	}

	@Test
	@DisplayName("검색 결과가 없으면 빈 Flux를 반환한다")
	void search_noResults_returnsEmpty() throws Exception {
		List<Float> queryEmbedding = List.of(0.1f);
		List<MemoryType> types = List.of(MemoryType.EXPERIENTIAL);

		when(qdrantClient.searchAsync(any(SearchPoints.class)))
			.thenReturn(Futures.immediateFuture(List.of()));

		StepVerifier.create(vectorDbAdapter.search(queryEmbedding, types, 0.5f, 5))
			.verifyComplete();
	}

	@Test
	@DisplayName("여러 메모리를 검색하여 반환한다")
	void search_multipleResults_returnsAll() throws Exception {
		List<Float> queryEmbedding = List.of(0.1f);
		List<MemoryType> types = List.of(MemoryType.EXPERIENTIAL);

		ScoredPoint point1 = ScoredPoint.newBuilder()
			.setId(Points.PointId.newBuilder().setUuid("doc-1").build())
			.putPayload("content",
				JsonWithInt.Value.newBuilder().setStringValue("첫 번째 메모리").build())
			.putPayload("type",
				JsonWithInt.Value.newBuilder().setStringValue("EXPERIENTIAL").build())
			.putPayload("importance", JsonWithInt.Value.newBuilder().setDoubleValue(0.9).build())
			.build();

		ScoredPoint point2 = ScoredPoint.newBuilder()
			.setId(Points.PointId.newBuilder().setUuid("doc-2").build())
			.putPayload("content",
				JsonWithInt.Value.newBuilder().setStringValue("두 번째 메모리").build())
			.putPayload("type",
				JsonWithInt.Value.newBuilder().setStringValue("EXPERIENTIAL").build())
			.putPayload("importance", JsonWithInt.Value.newBuilder().setDoubleValue(0.7).build())
			.build();

		when(qdrantClient.searchAsync(any(SearchPoints.class)))
			.thenReturn(Futures.immediateFuture(List.of(point1, point2)));

		StepVerifier.create(vectorDbAdapter.search(queryEmbedding, types, 0.5f, 5))
			.assertNext(result -> {
				assertThat(result.id()).isEqualTo("doc-1");
				assertThat(result.importance()).isEqualTo(0.9f);
			}).assertNext(result -> {
				assertThat(result.id()).isEqualTo("doc-2");
				assertThat(result.importance()).isEqualTo(0.7f);
			}).verifyComplete();
	}

	@Test
	@DisplayName("중요도 업데이트는 로그만 출력한다")
	void updateImportance_logsWarning() {
		String memoryId = "test-id";
		float newImportance = 0.9f;
		Instant lastAccessedAt = Instant.now();
		int accessCount = 10;

		StepVerifier
			.create(vectorDbAdapter
				.updateImportance(memoryId, newImportance, lastAccessedAt, accessCount))
			.verifyComplete();
	}

	@Test
	@DisplayName("ScoredPoint에서 Memory로 변환 시 모든 필드를 매핑한다")
	void search_mapsAllFields() throws Exception {
		Instant now = Instant.now();

		ScoredPoint point = ScoredPoint.newBuilder()
			.setId(Points.PointId.newBuilder().setUuid("doc-123").build())
			.putPayload("content", JsonWithInt.Value.newBuilder().setStringValue("완전한 메모리").build())
			.putPayload("type", JsonWithInt.Value.newBuilder().setStringValue("FACTUAL").build())
			.putPayload("importance", JsonWithInt.Value.newBuilder().setDoubleValue(0.85).build())
			.putPayload("createdAt",
				JsonWithInt.Value.newBuilder().setDoubleValue(now.minusSeconds(3600).toEpochMilli())
					.build())
			.putPayload("lastAccessedAt",
				JsonWithInt.Value.newBuilder().setDoubleValue(now.toEpochMilli()).build())
			.putPayload("accessCount", JsonWithInt.Value.newBuilder().setDoubleValue(3).build())
			.build();

		when(qdrantClient.searchAsync(any(SearchPoints.class)))
			.thenReturn(Futures.immediateFuture(List.of(point)));

		StepVerifier
			.create(vectorDbAdapter.search(List.of(0.1f), List.of(MemoryType.FACTUAL), 0.5f, 1))
			.assertNext(result -> {
				assertThat(result.id()).isEqualTo("doc-123");
				assertThat(result.content()).isEqualTo("완전한 메모리");
				assertThat(result.type()).isEqualTo(MemoryType.FACTUAL);
				assertThat(result.importance()).isEqualTo(0.85f);
				assertThat(result.createdAt()).isNotNull();
				assertThat(result.lastAccessedAt()).isNotNull();
				assertThat(result.accessCount()).isEqualTo(3);
			}).verifyComplete();
	}

	@Test
	@DisplayName("메타데이터에 선택적 필드가 없어도 정상 처리한다")
	void search_withMissingOptionalFields_success() throws Exception {
		ScoredPoint point = ScoredPoint.newBuilder()
			.setId(Points.PointId.newBuilder().setUuid("doc-min").build())
			.putPayload("content", JsonWithInt.Value.newBuilder().setStringValue("최소 메모리").build())
			.putPayload("type",
				JsonWithInt.Value.newBuilder().setStringValue("EXPERIENTIAL").build())
			.build();

		when(qdrantClient.searchAsync(any(SearchPoints.class)))
			.thenReturn(Futures.immediateFuture(List.of(point)));

		StepVerifier
			.create(
				vectorDbAdapter.search(List.of(0.1f), List.of(MemoryType.EXPERIENTIAL), 0.0f, 1))
			.assertNext(result -> {
				assertThat(result.id()).isEqualTo("doc-min");
				assertThat(result.type()).isEqualTo(MemoryType.EXPERIENTIAL);
				assertThat(result.importance()).isNull();
				assertThat(result.createdAt()).isNull();
				assertThat(result.lastAccessedAt()).isNull();
				assertThat(result.accessCount()).isNull();
			}).verifyComplete();
	}

	@Test
	@DisplayName("단일 타입으로 검색 시 Qdrant 검색이 호출된다")
	void search_singleType_createsCorrectFilter() throws Exception {
		List<MemoryType> types = List.of(MemoryType.FACTUAL);

		when(qdrantClient.searchAsync(any(SearchPoints.class)))
			.thenReturn(Futures.immediateFuture(List.of()));

		StepVerifier.create(vectorDbAdapter.search(List.of(0.1f), types, 0.3f, 5)).verifyComplete();

		verify(qdrantClient).searchAsync(any(SearchPoints.class));
	}
}
