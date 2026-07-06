package com.miyou.app.infrastructure.memory.adapter

import com.google.common.util.concurrent.Futures
import com.miyou.app.domain.memory.model.Memory
import com.miyou.app.domain.memory.model.MemoryEmotion
import com.miyou.app.domain.memory.model.MemoryType
import com.miyou.app.fixture.ConversationSessionFixture
import com.miyou.app.infrastructure.dialogue.config.properties.RagDialogueProperties
import com.miyou.app.support.anyValue
import com.miyou.app.support.eqValue
import io.qdrant.client.QdrantClient
import io.qdrant.client.grpc.JsonWithInt
import io.qdrant.client.grpc.Points
import io.qdrant.client.grpc.Points.ScoredPoint
import io.qdrant.client.grpc.Points.ScrollPoints
import io.qdrant.client.grpc.Points.ScrollResponse
import io.qdrant.client.grpc.Points.SearchPoints
import io.qdrant.client.grpc.Points.UpdateResult
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.ArgumentCaptor
import org.mockito.ArgumentMatchers.any
import org.mockito.ArgumentMatchers.nullable
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
        properties.qdrant.collectionName = "test-collection"
        vectorDbAdapter = SpringAiVectorDbAdapter(vectorStore, qdrantClient, properties)
    }

    @Test
    @DisplayName("upsert 시 메모리 메타데이터를 벡터 저장소에 저장한다")
    fun upsert_storesMemoryMetadata() {
        val sessionId = ConversationSessionFixture.createId()
        val memory =
            Memory(
                id = null,
                sessionId = sessionId,
                type = MemoryType.EXPERIENTIAL,
                content = "metadata test",
                importance = 0.9f,
                createdAt = Instant.parse("2025-01-01T00:00:00Z"),
                lastAccessedAt = Instant.parse("2025-01-02T00:00:00Z"),
                accessCount = 5,
                emotion = MemoryEmotion.POSITIVE,
            )

        @Suppress("UNCHECKED_CAST")
        val captor = ArgumentCaptor.forClass(List::class.java) as ArgumentCaptor<List<Document>>

        StepVerifier
            .create(vectorDbAdapter.upsert(memory, listOf(0.1f)))
            .assertNext { result -> assertThat(result.id).isNotNull() }
            .verifyComplete()

        verify(vectorStore).add(captor.capture())
        val metadata = captor.value.single().metadata
        assertThat(metadata["sessionId"]).isEqualTo(sessionId.value)
        assertThat(metadata["type"]).isEqualTo("EXPERIENTIAL")
        assertThat(metadata["importance"]).isEqualTo(0.9f)
        assertThat(metadata["accessCount"]).isEqualTo(5)
        assertThat(metadata["emotion"]).isEqualTo("POSITIVE")
    }

    @Test
    @DisplayName("검색 결과 Qdrant 포인트를 Memory 객체로 변환한다")
    fun search_mapsQdrantPointsIntoMemoryObjects() {
        val sessionId = ConversationSessionFixture.createId()
        val point =
            ScoredPoint
                .newBuilder()
                .setId(
                    Points.PointId
                        .newBuilder()
                        .setUuid("doc-1")
                        .build(),
                ).putPayload(
                    "doc_content",
                    JsonWithInt.Value
                        .newBuilder()
                        .setStringValue("test content")
                        .build(),
                ).putPayload(
                    "type",
                    JsonWithInt.Value
                        .newBuilder()
                        .setStringValue("EXPERIENTIAL")
                        .build(),
                ).putPayload(
                    "importance",
                    JsonWithInt.Value
                        .newBuilder()
                        .setDoubleValue(0.8)
                        .build(),
                ).build()

        `when`(qdrantClient.searchAsync(any(SearchPoints::class.java)))
            .thenReturn(Futures.immediateFuture(listOf(point)))

        StepVerifier
            .create(
                vectorDbAdapter.search(
                    sessionId,
                    listOf(0.1f, 0.2f),
                    listOf(MemoryType.EXPERIENTIAL),
                    0.5f,
                    5,
                ),
            ).assertNext { result ->
                assertThat(result.id).isEqualTo("doc-1")
                assertThat(result.content).isEqualTo("test content")
                assertThat(result.type).isEqualTo(MemoryType.EXPERIENTIAL)
            }.verifyComplete()
    }

    @Test
    @DisplayName("payload의 emotion 문자열을 MemoryEmotion으로 역직렬화한다")
    fun search_mapsEmotionFieldFromPayload() {
        val sessionId = ConversationSessionFixture.createId()
        val point =
            ScoredPoint
                .newBuilder()
                .setId(
                    Points.PointId
                        .newBuilder()
                        .setUuid("doc-2")
                        .build()
                ).putPayload(
                    "doc_content",
                    JsonWithInt.Value
                        .newBuilder()
                        .setStringValue("shocking content")
                        .build()
                ).putPayload(
                    "type",
                    JsonWithInt.Value
                        .newBuilder()
                        .setStringValue("EXPERIENTIAL")
                        .build()
                ).putPayload(
                    "importance",
                    JsonWithInt.Value
                        .newBuilder()
                        .setDoubleValue(0.4)
                        .build()
                ).putPayload(
                    "emotion",
                    JsonWithInt.Value
                        .newBuilder()
                        .setStringValue("SHOCKING")
                        .build()
                ).build()

        `when`(qdrantClient.searchAsync(any(SearchPoints::class.java)))
            .thenReturn(Futures.immediateFuture(listOf(point)))

        StepVerifier
            .create(
                vectorDbAdapter.search(sessionId, listOf(0.1f, 0.2f), listOf(MemoryType.EXPERIENTIAL), 0.5f, 5),
            ).assertNext { result -> assertThat(result.emotion).isEqualTo(MemoryEmotion.SHOCKING) }
            .verifyComplete()
    }

    @Test
    @DisplayName("updateImportance는 Qdrant payload를 갱신한다")
    fun updateImportance_writesPayloadToQdrant() {
        val memoryId = "550e8400-e29b-41d4-a716-446655440000"
        `when`(
            qdrantClient.setPayloadAsync(
                anyValue<String>(),
                anyValue<Map<String, JsonWithInt.Value>>(),
                anyValue<List<Points.PointId>>(),
                eqValue(true),
                nullable(Points.WriteOrderingType::class.java),
                nullable(java.time.Duration::class.java),
            ),
        ).thenReturn(Futures.immediateFuture(UpdateResult.getDefaultInstance()))

        StepVerifier
            .create(vectorDbAdapter.updateImportance(memoryId, 0.9f, Instant.now(), 10))
            .verifyComplete()
    }

    @Test
    @DisplayName("findAllActive는 archivedAt이 없는 포인트만 스크롤로 조회한다")
    fun findAllActive_scrollsOnlyNonArchivedPoints() {
        val point =
            Points.RetrievedPoint
                .newBuilder()
                .setId(
                    Points.PointId
                        .newBuilder()
                        .setUuid("mem-1")
                        .build()
                ).putPayload(
                    "sessionId",
                    JsonWithInt.Value
                        .newBuilder()
                        .setStringValue("session-1")
                        .build()
                ).putPayload(
                    "doc_content",
                    JsonWithInt.Value
                        .newBuilder()
                        .setStringValue("content")
                        .build()
                ).putPayload(
                    "type",
                    JsonWithInt.Value
                        .newBuilder()
                        .setStringValue("FACTUAL")
                        .build()
                ).putPayload(
                    "importance",
                    JsonWithInt.Value
                        .newBuilder()
                        .setDoubleValue(0.2)
                        .build()
                ).build()
        val response = ScrollResponse.newBuilder().addResult(point).build()

        `when`(qdrantClient.scrollAsync(any(ScrollPoints::class.java)))
            .thenReturn(Futures.immediateFuture(response))

        StepVerifier
            .create(vectorDbAdapter.findAllActive(100))
            .assertNext { memory ->
                assertThat(memory.id).isEqualTo("mem-1")
                assertThat(memory.sessionId.value).isEqualTo("session-1")
                assertThat(memory.archivedAt).isNull()
            }.verifyComplete()
    }

    @Test
    @DisplayName("applyDecayAndArchive는 importance와 archivedAt을 함께 반영한다")
    fun applyDecayAndArchive_writesImportanceAndArchivedAt() {
        val sessionId = ConversationSessionFixture.createId()
        val now = Instant.now()
        val archivedMemory =
            Memory(
                id = "550e8400-e29b-41d4-a716-446655440000",
                sessionId = sessionId,
                type = MemoryType.FACTUAL,
                content = "content",
                importance = 0.05f,
                createdAt = now,
                lastAccessedAt = now,
                accessCount = 1,
                archivedAt = now,
            )

        `when`(
            qdrantClient.setPayloadAsync(
                anyValue<String>(),
                anyValue<Map<String, JsonWithInt.Value>>(),
                anyValue<List<Points.PointId>>(),
                eqValue(true),
                nullable(Points.WriteOrderingType::class.java),
                nullable(java.time.Duration::class.java),
            ),
        ).thenReturn(Futures.immediateFuture(UpdateResult.getDefaultInstance()))

        StepVerifier
            .create(vectorDbAdapter.applyDecayAndArchive(archivedMemory))
            .verifyComplete()
    }
}
