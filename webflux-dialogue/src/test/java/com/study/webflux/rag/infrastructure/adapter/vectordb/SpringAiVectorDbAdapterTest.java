package com.study.webflux.rag.infrastructure.adapter.vectordb;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;

import com.study.webflux.rag.domain.model.memory.Memory;
import com.study.webflux.rag.domain.model.memory.MemoryType;

import reactor.test.StepVerifier;

@ExtendWith(MockitoExtension.class)
class SpringAiVectorDbAdapterTest {

	@Mock
	private VectorStore vectorStore;

	private SpringAiVectorDbAdapter vectorDbAdapter;

	@BeforeEach
	void setUp() {
		vectorDbAdapter = new SpringAiVectorDbAdapter(vectorStore);
	}

	@Test
	@DisplayName("메모리를 벡터 DB에 저장한다")
	void upsert_success() {
		Memory memory = new Memory(
			null,
			MemoryType.EXPERIENTIAL,
			"테스트 메모리",
			0.8f,
			Instant.now(),
			Instant.now(),
			1
		);
		List<Float> embedding = List.of(0.1f, 0.2f, 0.3f);

		StepVerifier.create(vectorDbAdapter.upsert(memory, embedding))
			.assertNext(result -> {
				assertThat(result).isNotNull();
				assertThat(result.id()).isNotNull();
				assertThat(result.content()).isEqualTo("테스트 메모리");
				assertThat(result.type()).isEqualTo(MemoryType.EXPERIENTIAL);
			})
			.verifyComplete();

		verify(vectorStore).add(anyList());
	}

	@Test
	@DisplayName("ID가 있는 메모리를 저장할 때 ID를 유지한다")
	void upsert_withExistingId_preservesId() {
		String existingId = "existing-id-123";
		Memory memory = new Memory(
			existingId,
			MemoryType.FACTUAL,
			"기존 메모리",
			0.5f,
			null,
			null,
			null
		);
		List<Float> embedding = List.of(0.1f, 0.2f);

		StepVerifier.create(vectorDbAdapter.upsert(memory, embedding))
			.assertNext(result -> {
				assertThat(result.id()).isEqualTo(existingId);
			})
			.verifyComplete();
	}

	@Test
	@DisplayName("메모리 저장 시 메타데이터를 올바르게 설정한다")
	void upsert_setsMetadataCorrectly() {
		Instant createdAt = Instant.parse("2025-01-01T00:00:00Z");
		Instant lastAccessedAt = Instant.parse("2025-01-02T00:00:00Z");

		Memory memory = new Memory(
			null,
			MemoryType.EXPERIENTIAL,
			"메타데이터 테스트",
			0.9f,
			createdAt,
			lastAccessedAt,
			5
		);
		List<Float> embedding = List.of(0.1f);

		ArgumentCaptor<List<Document>> captor = ArgumentCaptor.forClass(List.class);

		StepVerifier.create(vectorDbAdapter.upsert(memory, embedding))
			.assertNext(result -> assertThat(result).isNotNull())
			.verifyComplete();

		verify(vectorStore).add(captor.capture());
		Document capturedDoc = captor.getValue().get(0);
		Map<String, Object> metadata = capturedDoc.getMetadata();

		assertThat(metadata.get("type")).isEqualTo("EXPERIENTIAL");
		assertThat(metadata.get("importance")).isEqualTo(0.9f);
		assertThat(metadata.get("createdAt")).isEqualTo(createdAt.getEpochSecond());
		assertThat(metadata.get("lastAccessedAt")).isEqualTo(lastAccessedAt.getEpochSecond());
		assertThat(metadata.get("accessCount")).isEqualTo(5);
	}

	@Test
	@DisplayName("타입과 중요도 필터로 메모리를 검색한다")
	void search_withFilters_success() {
		List<Float> queryEmbedding = List.of(0.1f, 0.2f, 0.3f);
		List<MemoryType> types = List.of(MemoryType.EXPERIENTIAL, MemoryType.FACTUAL);
		float importanceThreshold = 0.5f;
		int topK = 5;

		Map<String, Object> metadata = new HashMap<>();
		metadata.put("type", "EXPERIENTIAL");
		metadata.put("importance", 0.8);
		metadata.put("createdAt", Instant.now().getEpochSecond());

		Document mockDocument = new Document("doc-1", "테스트 내용", metadata);
		List<Document> mockResults = List.of(mockDocument);

		when(vectorStore.similaritySearch(any(SearchRequest.class))).thenReturn(mockResults);

		StepVerifier.create(vectorDbAdapter.search(queryEmbedding, types, importanceThreshold, topK))
			.assertNext(result -> {
				assertThat(result).isNotNull();
				assertThat(result.id()).isEqualTo("doc-1");
				assertThat(result.content()).isEqualTo("테스트 내용");
				assertThat(result.type()).isEqualTo(MemoryType.EXPERIENTIAL);
				assertThat(result.importance()).isEqualTo(0.8f);
			})
			.verifyComplete();

		ArgumentCaptor<SearchRequest> captor = ArgumentCaptor.forClass(SearchRequest.class);
		verify(vectorStore).similaritySearch(captor.capture());

		SearchRequest capturedRequest = captor.getValue();
		assertThat(capturedRequest.getTopK()).isEqualTo(topK);
		String expressionDescription = String.valueOf(capturedRequest.getFilterExpression());
		assertThat(expressionDescription).contains("Value[value=[EXPERIENTIAL, FACTUAL]]");
		assertThat(expressionDescription).contains("Key[key=importance]");
		assertThat(expressionDescription).contains("Value[value=0.5]");
	}

	@Test
	@DisplayName("검색 결과가 없으면 빈 Flux를 반환한다")
	void search_noResults_returnsEmpty() {
		List<Float> queryEmbedding = List.of(0.1f);
		List<MemoryType> types = List.of(MemoryType.EXPERIENTIAL);

		when(vectorStore.similaritySearch(any(SearchRequest.class))).thenReturn(List.of());

		StepVerifier.create(vectorDbAdapter.search(queryEmbedding, types, 0.5f, 5))
			.verifyComplete();
	}

	@Test
	@DisplayName("여러 메모리를 검색하여 반환한다")
	void search_multipleResults_returnsAll() {
		List<Float> queryEmbedding = List.of(0.1f);
		List<MemoryType> types = List.of(MemoryType.EXPERIENTIAL);

		Map<String, Object> metadata1 = new HashMap<>();
		metadata1.put("type", "EXPERIENTIAL");
		metadata1.put("importance", 0.9);

		Map<String, Object> metadata2 = new HashMap<>();
		metadata2.put("type", "EXPERIENTIAL");
		metadata2.put("importance", 0.7);

		Document doc1 = new Document("doc-1", "첫 번째 메모리", metadata1);
		Document doc2 = new Document("doc-2", "두 번째 메모리", metadata2);

		when(vectorStore.similaritySearch(any(SearchRequest.class)))
			.thenReturn(List.of(doc1, doc2));

		StepVerifier.create(vectorDbAdapter.search(queryEmbedding, types, 0.5f, 5))
			.assertNext(result -> {
				assertThat(result.id()).isEqualTo("doc-1");
				assertThat(result.importance()).isEqualTo(0.9f);
			})
			.assertNext(result -> {
				assertThat(result.id()).isEqualTo("doc-2");
				assertThat(result.importance()).isEqualTo(0.7f);
			})
			.verifyComplete();
	}

	@Test
	@DisplayName("중요도 업데이트는 로그만 출력한다")
	void updateImportance_logsWarning() {
		String memoryId = "test-id";
		float newImportance = 0.9f;
		Instant lastAccessedAt = Instant.now();
		int accessCount = 10;

		StepVerifier.create(
			vectorDbAdapter.updateImportance(memoryId, newImportance, lastAccessedAt, accessCount)
		).verifyComplete();
	}

	@Test
	@DisplayName("Document에서 Memory로 변환 시 모든 필드를 매핑한다")
	void search_mapsAllFields() {
		Instant now = Instant.now();

		Map<String, Object> metadata = new HashMap<>();
		metadata.put("type", "FACTUAL");
		metadata.put("importance", 0.85);
		metadata.put("createdAt", now.minusSeconds(3600).getEpochSecond());
		metadata.put("lastAccessedAt", now.getEpochSecond());
		metadata.put("accessCount", 3);

		Document document = new Document("doc-123", "완전한 메모리", metadata);

		when(vectorStore.similaritySearch(any(SearchRequest.class)))
			.thenReturn(List.of(document));

		StepVerifier.create(
			vectorDbAdapter.search(List.of(0.1f), List.of(MemoryType.FACTUAL), 0.5f, 1)
		)
			.assertNext(result -> {
				assertThat(result.id()).isEqualTo("doc-123");
				assertThat(result.content()).isEqualTo("완전한 메모리");
				assertThat(result.type()).isEqualTo(MemoryType.FACTUAL);
				assertThat(result.importance()).isEqualTo(0.85f);
				assertThat(result.createdAt()).isNotNull();
				assertThat(result.lastAccessedAt()).isNotNull();
				assertThat(result.accessCount()).isEqualTo(3);
			})
			.verifyComplete();
	}

	@Test
	@DisplayName("메타데이터에 선택적 필드가 없어도 정상 처리한다")
	void search_withMissingOptionalFields_success() {
		Map<String, Object> metadata = new HashMap<>();
		metadata.put("type", "EXPERIENTIAL");

		Document document = new Document("doc-min", "최소 메모리", metadata);

		when(vectorStore.similaritySearch(any(SearchRequest.class)))
			.thenReturn(List.of(document));

		StepVerifier.create(
			vectorDbAdapter.search(List.of(0.1f), List.of(MemoryType.EXPERIENTIAL), 0.0f, 1)
		)
			.assertNext(result -> {
				assertThat(result.id()).isEqualTo("doc-min");
				assertThat(result.type()).isEqualTo(MemoryType.EXPERIENTIAL);
				assertThat(result.importance()).isNull();
				assertThat(result.createdAt()).isNull();
				assertThat(result.lastAccessedAt()).isNull();
				assertThat(result.accessCount()).isNull();
			})
			.verifyComplete();
	}

	@Test
	@DisplayName("단일 타입으로 검색 시 필터 표현식이 올바르게 생성된다")
	void search_singleType_createsCorrectFilter() {
		List<MemoryType> types = List.of(MemoryType.FACTUAL);

		when(vectorStore.similaritySearch(any(SearchRequest.class))).thenReturn(List.of());

		StepVerifier.create(vectorDbAdapter.search(List.of(0.1f), types, 0.3f, 5))
			.verifyComplete();

		ArgumentCaptor<SearchRequest> captor = ArgumentCaptor.forClass(SearchRequest.class);
		verify(vectorStore).similaritySearch(captor.capture());

		String filterExpression = String.valueOf(captor.getValue().getFilterExpression());
		assertThat(filterExpression).contains("Value[value=[FACTUAL]]");
		assertThat(filterExpression).contains("Key[key=importance]");
		assertThat(filterExpression).contains("Value[value=0.3]");
	}
}
