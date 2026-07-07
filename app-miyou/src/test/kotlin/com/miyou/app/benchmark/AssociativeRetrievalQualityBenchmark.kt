package com.miyou.app.benchmark

import com.miyou.app.application.memory.policy.MemoryRetrievalPolicy
import com.miyou.app.application.memory.service.MemoryRetrievalService
import com.miyou.app.domain.dialogue.model.ConversationSessionId
import com.miyou.app.domain.memory.model.Memory
import com.miyou.app.domain.memory.model.MemoryType
import com.miyou.app.domain.memory.port.EmbeddingPort
import com.miyou.app.domain.memory.port.VectorMemoryPort
import com.miyou.app.infrastructure.dialogue.config.properties.RagDialogueProperties
import com.miyou.app.infrastructure.memory.adapter.SpringAiEmbeddingAdapter
import com.miyou.app.infrastructure.memory.adapter.SpringAiVectorDbAdapter
import com.miyou.app.monitoring.port.RagQualityMetricsPort
import io.qdrant.client.QdrantClient
import io.qdrant.client.QdrantGrpcClient
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.springframework.ai.document.MetadataMode
import org.springframework.ai.openai.OpenAiEmbeddingModel
import org.springframework.ai.openai.OpenAiEmbeddingOptions
import org.springframework.ai.openai.api.OpenAiApi
import org.springframework.ai.vectorstore.qdrant.QdrantVectorStore
import org.testcontainers.containers.GenericContainer
import org.testcontainers.utility.DockerImageName
import java.time.Instant
import java.util.UUID

/**
 * 연상 기반 다단계 회상(Issue #79)이 실제로 "쿼리와는 거리가 멀지만 1차 검색 결과와
 * 강하게 연관된 기억"을 끌어오는지 실측하는 벤치마크.
 *
 * 큐레이터/추출 confidence 벤치마크와 달리 이 기능은 기본 OFF로 병합되었다 - 이
 * 벤치마크는 "켜면 실제로 도움이 되는가"를 판단하기 위한 자료를 만드는 것이 목적이며,
 * 결과를 보고 기본값 전환 여부를 별도로 결정한다.
 *
 * 요구사항: `OPENAI_API_KEY` 환경변수, 로컬 Docker 데몬. 둘 중 하나라도 없으면 스킵된다.
 */
@Tag("benchmark")
@DisplayName("[벤치마크] 연상 기반 다단계 회상 - 활성화 전/후 비교")
class AssociativeRetrievalQualityBenchmark {
    companion object {
        private const val TOP_K = 2

        private val qdrantContainerLazy: Lazy<GenericContainer<Nothing>> =
            lazy {
                GenericContainer<Nothing>(DockerImageName.parse("qdrant/qdrant:v1.9.1"))
                    .withExposedPorts(6333, 6334)
                    .apply { start() }
            }
        private val qdrantContainer: GenericContainer<Nothing> by qdrantContainerLazy

        @JvmStatic
        @AfterAll
        fun stopContainer() {
            if (qdrantContainerLazy.isInitialized() && qdrantContainer.isRunning) {
                qdrantContainer.stop()
            }
        }
    }

    @Test
    fun comparesTopKMembershipWithAndWithoutAssociativeHop() {
        val apiKey = System.getenv("OPENAI_API_KEY")
        assumeTrue(!apiKey.isNullOrBlank()) { "OPENAI_API_KEY 환경변수가 없어 벤치마크를 건너뜁니다" }

        val collectionName = "benchmark_associative_${UUID.randomUUID()}"
        val qdrantClient =
            QdrantClient(
                QdrantGrpcClient.newBuilder(qdrantContainer.host, qdrantContainer.getMappedPort(6334), false).build(),
            )
        val embeddingModel =
            OpenAiEmbeddingModel(
                OpenAiApi(apiKey),
                MetadataMode.EMBED,
                OpenAiEmbeddingOptions.builder().model("text-embedding-3-small").build(),
            )
        val vectorStore =
            QdrantVectorStore
                .builder(qdrantClient, embeddingModel)
                .collectionName(collectionName)
                .initializeSchema(true)
                .build()
        vectorStore.afterPropertiesSet()

        val properties = RagDialogueProperties().apply { qdrant.collectionName = collectionName }
        val embeddingPort = SpringAiEmbeddingAdapter(embeddingModel)
        val vectorMemoryPort = SpringAiVectorDbAdapter(vectorStore, qdrantClient, properties)
        val metrics = NoOpRagQualityMetricsPort

        val sessionId = ConversationSessionId.generate().value
        val query = "라면 얘기 좀 해줘"

        // trigger: 쿼리와 직접 관련 있는 진짜 신호. 항상 1차 검색 1위가 되도록 importance를 높게 둔다.
        val triggerContent = "사용자는 지난주 목요일 밤에 편의점에서 라면을 먹었다"
        // associative: "라면"은 언급 안 하지만 trigger와 같은 장면(편의점)을 공유 - 쿼리 직접 유사도는
        // 낮지만 trigger 임베딩과는 가깝다. 연상 홉이 없으면 topK에 못 들어오는 게 이 벤치마크의 관심사.
        val associativeContent = "사용자는 그날 편의점에서 우연히 오랜 친구 민지를 만났다"
        val distractorContents =
            listOf(
                "사용자는 매일 아침 요가를 한다",
                "사용자는 최근 이직을 준비하고 있다",
                "사용자는 다음 달에 제주도 여행을 간다",
            )

        val now = Instant.now()
        seedMemory(embeddingPort, vectorMemoryPort, sessionId, triggerContent, importance = 0.6f, now)
        // distractor보다 importance를 높여 - 연상으로 후보군에 들어오기만 하면 랭킹에서
        // 확실히 이기도록(1차 primary 후보 window에 우연히 든 distractor와의 동점 방지).
        seedMemory(embeddingPort, vectorMemoryPort, sessionId, associativeContent, importance = 0.65f, now)
        distractorContents.forEach { content ->
            seedMemory(embeddingPort, vectorMemoryPort, sessionId, content, importance = 0.35f, now)
        }

        fun runWith(
            hopEnabled: Boolean,
            hopTopK: Int,
        ): List<String> =
            MemoryRetrievalService(
                embeddingPort,
                vectorMemoryPort,
                metrics,
                MemoryRetrievalPolicy(
                    0.05f,
                    0.3f,
                    associativeHopEnabled = hopEnabled,
                    associativeHopTopK = hopTopK,
                    associativeHopMinScore = 0.3f
                ),
            ).retrieveMemories(sessionId, query, TOP_K).block()!!.allMemories().map(Memory::content)

        val withoutHop = runWith(hopEnabled = false, hopTopK = 2)
        // 기본값(2)과, 더 넓은 홉(5)을 나란히 비교한다 - 홉 폭이 실제로 결과에 영향을
        // 주는지가 이 벤치마크의 핵심 관측 대상 중 하나다.
        val withHopDefault = runWith(hopEnabled = true, hopTopK = 2)
        val withHopWide = runWith(hopEnabled = true, hopTopK = 5)

        println(
            """

            === 연상 기반 다단계 회상 벤치마크 ===
            쿼리: "$query" (top-$TOP_K)
            trigger(항상 1위 기대): "$triggerContent"
            associative(연상 대상, 쿼리엔 직접 안 나타남): "$associativeContent"
            비활성화                 - 반환: $withoutHop
            활성화(hopTopK=2, 기본값) - 반환: $withHopDefault
            활성화(hopTopK=5, 넓게)   - 반환: $withHopWide
            associative 포함 여부 - 비활성화: ${associativeContent in withoutHop}, hopTopK=2: ${
                associativeContent in withHopDefault
            }, hopTopK=5: ${associativeContent in withHopWide}
            =======================================

            """.trimIndent(),
        )

        // 기능 자체의 품질 우열은 이 벤치마크의 판단 대상이 아니다(그래서 하드 assert 없음) -
        // 최소한 harness가 깨지지 않고 trigger는 항상 회수되는지만 sanity check한다.
        assertThat(withoutHop).contains(triggerContent)
        assertThat(withHopDefault).contains(triggerContent)
        assertThat(withHopWide).contains(triggerContent)
    }

    private fun seedMemory(
        embeddingPort: EmbeddingPort,
        vectorMemoryPort: VectorMemoryPort,
        sessionId: String,
        content: String,
        importance: Float,
        now: Instant,
    ) {
        val memory =
            Memory(
                id = null,
                sessionId = sessionId,
                type = MemoryType.EXPERIENTIAL,
                content = content,
                importance = importance,
                createdAt = now,
                lastAccessedAt = now,
                accessCount = 1,
            )
        val embedding = embeddingPort.embed(content).block()!!
        vectorMemoryPort.upsert(memory, embedding.vector).block()
    }

    private object NoOpRagQualityMetricsPort : RagQualityMetricsPort {
        override fun recordMemoryCandidateCount(count: Int) = Unit

        override fun recordMemoryFilteredCount(count: Int) = Unit

        override fun recordMemorySimilarityScore(score: Double) = Unit

        override fun recordMemoryImportanceScore(importance: Double) = Unit

        override fun recordDocumentRelevanceScore(score: Double) = Unit
    }
}
