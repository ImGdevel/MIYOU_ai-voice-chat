package com.miyou.app.infrastructure.memory.adapter

import com.miyou.app.domain.dialogue.model.ConversationSessionId
import com.miyou.app.domain.memory.model.Memory
import com.miyou.app.domain.memory.model.MemoryType
import com.miyou.app.domain.memory.port.VectorMemoryPort
import com.miyou.app.infrastructure.dialogue.config.properties.RagDialogueProperties
import io.qdrant.client.QdrantClient
import io.qdrant.client.grpc.Points.Condition
import io.qdrant.client.grpc.Points.FieldCondition
import io.qdrant.client.grpc.Points.Filter
import io.qdrant.client.grpc.Points.Match
import io.qdrant.client.grpc.Points.Range
import io.qdrant.client.grpc.Points.ScoredPoint
import io.qdrant.client.grpc.Points.SearchPoints
import io.qdrant.client.grpc.Points.WithPayloadSelector
import org.slf4j.LoggerFactory
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
    private val log = LoggerFactory.getLogger(SpringAiVectorDbAdapter::class.java)
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
                metadata["createdAt"] = memory.createdAt.toEpochMilli()
                memory.lastAccessedAt?.let { metadata["lastAccessedAt"] = it.toEpochMilli() }
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
        Mono
            .fromCallable<Void> {
                log.warn("Memory ID={} importance update is not supported by current Spring AI config", memoryId)
                null
            }.subscribeOn(Schedulers.boundedElastic())
            .then()

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

        val content = payload["content"]?.stringValue ?: ""

        val typeStr =
            payload["type"]?.stringValue
                ?: throw IllegalStateException("Point $id has no type")

        val type = MemoryType.valueOf(typeStr)
        val importance = payload["importance"]?.doubleValue?.toFloat()
        val createdAt = payload["createdAt"]?.doubleValue?.let { Instant.ofEpochMilli(it.toLong()) }
        val lastAccessedAt = payload["lastAccessedAt"]?.doubleValue?.let { Instant.ofEpochMilli(it.toLong()) }
        val accessCount = payload["accessCount"]?.doubleValue?.toInt()

        return Memory(
            id,
            sessionId,
            type,
            content,
            importance,
            createdAt = createdAt ?: Instant.now(),
            lastAccessedAt = lastAccessedAt,
            accessCount = accessCount,
        )
    }
}
