package com.miyou.app.infrastructure.memory.adapter

import com.miyou.app.domain.dialogue.model.ConversationSessionId
import com.miyou.app.domain.memory.model.Memory
import com.miyou.app.domain.memory.model.MemoryType
import com.miyou.app.domain.memory.port.VectorMemoryPort
import com.miyou.app.infrastructure.dialogue.config.properties.RagDialogueProperties
import io.github.oshai.kotlinlogging.KotlinLogging
import io.qdrant.client.ConditionFactory.isEmpty
import io.qdrant.client.PointIdFactory.id
import io.qdrant.client.QdrantClient
import io.qdrant.client.ValueFactory.value
import io.qdrant.client.grpc.JsonWithInt.Value
import io.qdrant.client.grpc.Points.Condition
import io.qdrant.client.grpc.Points.FieldCondition
import io.qdrant.client.grpc.Points.Filter
import io.qdrant.client.grpc.Points.Match
import io.qdrant.client.grpc.Points.PointId
import io.qdrant.client.grpc.Points.Range
import io.qdrant.client.grpc.Points.RetrievedPoint
import io.qdrant.client.grpc.Points.ScoredPoint
import io.qdrant.client.grpc.Points.ScrollPoints
import io.qdrant.client.grpc.Points.SearchPoints
import io.qdrant.client.grpc.Points.WithPayloadSelector
import org.springframework.ai.document.Document
import org.springframework.ai.vectorstore.VectorStore
import org.springframework.context.annotation.Primary
import org.springframework.stereotype.Component
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import reactor.core.scheduler.Schedulers
import java.time.Instant
import java.util.HashMap
import java.util.UUID

@Primary
@Component
class SpringAiVectorDbAdapter(
    private val vectorStore: VectorStore,
    private val qdrantClient: QdrantClient,
    properties: RagDialogueProperties,
) : VectorMemoryPort {
    private val log = KotlinLogging.logger {}
    private val collectionName = properties.qdrant.collectionName

    override fun upsert(
        memory: Memory,
        embedding: List<Float>,
    ): Mono<Memory> =
        Mono
            .fromCallable {
                val id = memory.id ?: UUID.randomUUID().toString()

                val metadata = HashMap<String, Any>()
                metadata["sessionId"] = memory.sessionId.value
                metadata["type"] = memory.type.name
                memory.importance?.let { metadata["importance"] = it }
                // Spring AI QdrantVectorStore(1.0.0-M5)의 payload 변환은 Long을 지원하지 않는다
                // (String/Integer/Double/Float/Boolean/Map만 허용) - epoch millis를 Double로 저장.
                metadata["createdAt"] = memory.createdAt.toEpochMilli().toDouble()
                memory.lastAccessedAt?.let { metadata["lastAccessedAt"] = it.toEpochMilli().toDouble() }
                memory.accessCount?.let { metadata["accessCount"] = it }

                val document = Document(id, memory.content, metadata)
                vectorStore.add(listOf(document))

                memory.withId(id)
            }.subscribeOn(Schedulers.boundedElastic())

    override fun search(
        sessionId: ConversationSessionId,
        queryEmbedding: List<Float>,
        types: List<MemoryType>,
        importanceThreshold: Float,
        topK: Int,
    ): Flux<Memory> =
        Mono
            .fromCallable {
                val filterBuilder = Filter.newBuilder()
                filterBuilder.addMust(
                    Condition
                        .newBuilder()
                        .setField(
                            FieldCondition
                                .newBuilder()
                                .setKey("sessionId")
                                .setMatch(
                                    Match
                                        .newBuilder()
                                        .setKeyword(sessionId.value)
                                        .build(),
                                ).build(),
                        ).build(),
                )
                filterBuilder.addMust(isEmpty(ARCHIVED_AT_KEY))

                if (importanceThreshold > 0) {
                    filterBuilder.addMust(
                        Condition
                            .newBuilder()
                            .setField(
                                FieldCondition
                                    .newBuilder()
                                    .setKey("importance")
                                    .setRange(Range.newBuilder().setGte(importanceThreshold.toDouble()).build())
                                    .build(),
                            ).build(),
                    )
                }

                if (types.isNotEmpty()) {
                    val typeFilterBuilder = Filter.newBuilder()
                    types.forEach { type ->
                        typeFilterBuilder.addShould(
                            Condition
                                .newBuilder()
                                .setField(
                                    FieldCondition
                                        .newBuilder()
                                        .setKey("type")
                                        .setMatch(Match.newBuilder().setKeyword(type.name).build())
                                        .build(),
                                ).build(),
                        )
                    }
                    filterBuilder.addMust(Condition.newBuilder().setFilter(typeFilterBuilder.build()).build())
                }

                val searchPoints =
                    SearchPoints
                        .newBuilder()
                        .setCollectionName(collectionName)
                        .addAllVector(queryEmbedding)
                        .setLimit(topK.toLong())
                        .setWithPayload(WithPayloadSelector.newBuilder().setEnable(true).build())
                        .setFilter(filterBuilder.build())
                        .build()

                qdrantClient.searchAsync(searchPoints).get()
            }.subscribeOn(Schedulers.boundedElastic())
            .flatMapMany { results ->
                val memories = results.map { point -> toMemoryFromScoredPoint(point, sessionId) }
                Flux.fromIterable(memories)
            }

    override fun updateImportance(
        memoryId: String,
        newImportance: Float,
        lastAccessedAt: Instant,
        accessCount: Int,
    ): Mono<Void> =
        writePayload(
            memoryId,
            mapOf(
                "importance" to value(newImportance.toDouble()),
                "lastAccessedAt" to value(lastAccessedAt.toEpochMilli()),
                "accessCount" to value(accessCount.toLong()),
            ),
        )

    override fun findAllActive(batchSize: Int): Flux<Memory> = scrollActivePage(batchSize, null)

    override fun applyDecayAndArchive(memory: Memory): Mono<Void> {
        val memoryId = memory.id ?: return Mono.empty()
        val payload = mutableMapOf<String, Value>()
        memory.importance?.let { payload["importance"] = value(it.toDouble()) }
        memory.archivedAt?.let {
            payload[ARCHIVED_AT_KEY] = value(it.toEpochMilli())
            log.info { "메모리 아카이브 처리 id=$memoryId importance=${memory.importance}" }
        }
        if (payload.isEmpty()) {
            return Mono.empty()
        }
        return writePayload(memoryId, payload)
    }

    private fun writePayload(
        memoryId: String,
        payload: Map<String, Value>,
    ): Mono<Void> =
        Mono
            .fromCallable<Void> {
                qdrantClient
                    .setPayloadAsync(
                        collectionName,
                        payload,
                        listOf(id(UUID.fromString(memoryId))),
                        true,
                        null,
                        null
                    ).get()
                null
            }.subscribeOn(Schedulers.boundedElastic())
            .then()

    private fun scrollActivePage(
        batchSize: Int,
        offset: PointId?,
    ): Flux<Memory> =
        Mono
            .fromCallable {
                val builder =
                    ScrollPoints
                        .newBuilder()
                        .setCollectionName(collectionName)
                        .setFilter(Filter.newBuilder().addMust(isEmpty(ARCHIVED_AT_KEY)).build())
                        .setLimit(batchSize)
                        .setWithPayload(WithPayloadSelector.newBuilder().setEnable(true).build())
                offset?.let { builder.setOffset(it) }
                qdrantClient.scrollAsync(builder.build()).get()
            }.subscribeOn(Schedulers.boundedElastic())
            .flatMapMany { response ->
                val page = Flux.fromIterable(response.resultList.map(::toMemoryFromRetrievedPoint))
                if (response.hasNextPageOffset()) {
                    page.concatWith(Flux.defer { scrollActivePage(batchSize, response.nextPageOffset) })
                } else {
                    page
                }
            }

    private fun toMemoryFromScoredPoint(
        point: ScoredPoint,
        sessionId: ConversationSessionId,
    ): Memory {
        val payload = point.payloadMap
        val id =
            if (point.id.hasNum()) {
                point.id.num.toString()
            } else {
                point.id.uuid
            }
        return toMemory(id, sessionId, payload)
    }

    private fun toMemoryFromRetrievedPoint(point: RetrievedPoint): Memory {
        val payload = point.payloadMap
        val id =
            if (point.id.hasNum()) {
                point.id.num.toString()
            } else {
                point.id.uuid
            }
        val sessionId = ConversationSessionId.of(payload["sessionId"]?.stringValue ?: id)
        return toMemory(id, sessionId, payload)
    }

    private fun toMemory(
        id: String,
        sessionId: ConversationSessionId,
        payload: Map<String, Value>,
    ): Memory {
        // Spring AI QdrantVectorStore는 Document 텍스트를 "content"가 아니라 "doc_content" 페이로드
        // 키에 저장한다(내부 상수 CONTENT_FIELD_NAME) - 실제 Qdrant에서는 이 키로만 읽힌다.
        val content = payload[CONTENT_FIELD_NAME]?.stringValue ?: ""

        val typeStr =
            payload["type"]?.stringValue
                ?: throw IllegalStateException("Point $id has no type")

        val type = MemoryType.valueOf(typeStr)
        val importance = payload["importance"]?.doubleValue?.toFloat()
        val createdAt = payload["createdAt"]?.doubleValue?.let { Instant.ofEpochMilli(it.toLong()) }
        val lastAccessedAt = payload["lastAccessedAt"]?.doubleValue?.let { Instant.ofEpochMilli(it.toLong()) }
        val accessCount = payload["accessCount"]?.doubleValue?.toInt()
        val archivedAt = payload[ARCHIVED_AT_KEY]?.doubleValue?.let { Instant.ofEpochMilli(it.toLong()) }

        return Memory(
            id,
            sessionId,
            type,
            content,
            importance,
            createdAt = createdAt ?: Instant.now(),
            lastAccessedAt = lastAccessedAt,
            accessCount = accessCount,
            archivedAt = archivedAt,
        )
    }

    private companion object {
        const val ARCHIVED_AT_KEY = "archivedAt"
        const val CONTENT_FIELD_NAME = "doc_content"
    }
}
