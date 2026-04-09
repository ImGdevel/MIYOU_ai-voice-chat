package com.miyou.app.infrastructure.memory.adapter

import com.google.common.util.concurrent.Futures
import com.miyou.app.domain.memory.model.Memory
import com.miyou.app.domain.memory.model.MemoryType
import com.miyou.app.fixture.ConversationSessionFixture
import com.miyou.app.infrastructure.dialogue.config.properties.RagDialogueProperties
import io.qdrant.client.QdrantClient
import io.qdrant.client.grpc.JsonWithInt
import io.qdrant.client.grpc.Points
import io.qdrant.client.grpc.Points.ScoredPoint
import io.qdrant.client.grpc.Points.SearchPoints
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.ArgumentCaptor
import org.mockito.ArgumentMatchers.any
import org.mockito.Mock
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
import org.mockito.junit.jupiter.MockitoExtension
import org.springframework.ai.document.Document
import org.springframework.ai.vectorstore.VectorStore
import reactor.test.StepVerifier
import java.time.Instant

@ExtendWith(MockitoExtension::class)
class SpringAiVectorDbAdapterTest {

	@Mock
	private lateinit var vectorStore: VectorStore

	@Mock
	private lateinit var qdrantClient: QdrantClient

	private lateinit var vectorDbAdapter: SpringAiVectorDbAdapter

	@BeforeEach
	fun setUp() {
		val properties = RagDialogueProperties()
		val qdrant = RagDialogueProperties.Qdrant()
		qdrant.collectionName = "test-collection"
		properties.qdrant = qdrant
		vectorDbAdapter = SpringAiVectorDbAdapter(vectorStore, qdrantClient, properties)
	}

	@Test
	@DisplayName("upsert는 메타데이터를 포함해 벡터 저장소에 저장한다")
	fun upsert_setsMetadataCorrectly() {
		val sessionId = ConversationSessionFixture.createId()
		val memory = Memory(
			null,
			sessionId,
			MemoryType.EXPERIENTIAL,
			"metadata test",
			0.9f,
			Instant.parse("2025-01-01T00:00:00Z"),
			Instant.parse("2025-01-02T00:00:00Z"),
			5,
		)
		val captor = ArgumentCaptor.forClass(List::class.java) as ArgumentCaptor<List<Document>>

		StepVerifier.create(vectorDbAdapter.upsert(memory, listOf(0.1f)))
			.assertNext { result -> assertThat(result.id()).isNotNull() }
			.verifyComplete()

		verify(vectorStore).add(captor.capture())
		val metadata = captor.value[0].metadata
		assertThat(metadata["sessionId"]).isEqualTo(sessionId.value())
		assertThat(metadata["type"]).isEqualTo("EXPERIENTIAL")
		assertThat(metadata["importance"]).isEqualTo(0.9f)
		assertThat(metadata["accessCount"]).isEqualTo(5)
	}

	@Test
	@DisplayName("search는 Qdrant 결과를 Memory로 매핑한다")
	fun search_withFilters_success() {
		val sessionId = ConversationSessionFixture.createId()
		val point = ScoredPoint.newBuilder()
			.setId(Points.PointId.newBuilder().setUuid("doc-1").build())
			.putPayload("content", JsonWithInt.Value.newBuilder().setStringValue("테스트 내용").build())
			.putPayload("type", JsonWithInt.Value.newBuilder().setStringValue("EXPERIENTIAL").build())
			.putPayload("importance", JsonWithInt.Value.newBuilder().setDoubleValue(0.8).build())
			.build()

		`when`(qdrantClient.searchAsync(any(SearchPoints::class.java)))
			.thenReturn(Futures.immediateFuture(listOf(point)))

		StepVerifier.create(
			vectorDbAdapter.search(sessionId, listOf(0.1f, 0.2f), listOf(MemoryType.EXPERIENTIAL), 0.5f, 5),
		)
			.assertNext { result ->
				assertThat(result.id()).isEqualTo("doc-1")
				assertThat(result.content()).isEqualTo("테스트 내용")
				assertThat(result.type()).isEqualTo(MemoryType.EXPERIENTIAL)
			}
			.verifyComplete()
	}

	@Test
	@DisplayName("updateImportance는 현재 no-op 으로 완료된다")
	fun updateImportance_logsWarning() {
		StepVerifier.create(vectorDbAdapter.updateImportance("test-id", 0.9f, Instant.now(), 10))
			.verifyComplete()
	}
}
