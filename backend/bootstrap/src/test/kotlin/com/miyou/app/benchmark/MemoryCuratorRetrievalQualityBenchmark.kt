package com.miyou.app.benchmark

import com.miyou.app.application.memory.policy.MemoryCuratorPolicy
import com.miyou.app.application.memory.service.MemoryCuratorService
import com.miyou.app.domain.dialogue.model.ConversationSessionId
import com.miyou.app.domain.memory.model.Memory
import com.miyou.app.domain.memory.model.MemoryType
import com.miyou.app.domain.memory.port.EmbeddingPort
import com.miyou.app.domain.memory.port.VectorMemoryPort
import com.miyou.app.domain.memory.service.MemoryDecayService
import com.miyou.app.infrastructure.dialogue.config.properties.RagDialogueProperties
import com.miyou.app.infrastructure.memory.adapter.SpringAiEmbeddingAdapter
import com.miyou.app.infrastructure.memory.adapter.SpringAiVectorDbAdapter
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
import java.time.temporal.ChronoUnit
import java.util.UUID

/**
 * 메모리 큐레이터(감쇠 + 소프트 아카이브) 적용 전/후 검색 품질 비교 벤치마크.
 *
 * 실제 OpenAI 임베딩 API + 실제 Qdrant(Testcontainers)를 사용한다 - 비용이 들고
 * 임베딩 모델 버전 등에 따라 결과가 조금씩 달라질 수 있어 CI 기본 `test` 태스크에서는
 * 제외되고 `./gradlew memoryCuratorBenchmark`로만 수동 실행된다.
 *
 * 요구사항: `OPENAI_API_KEY` 환경변수, 로컬 Docker 데몬. 둘 중 하나라도 없으면 스킵된다.
 */
@Tag("benchmark")
@DisplayName("[벤치마크] 메모리 큐레이터 적용 전/후 검색 품질 비교")
class MemoryCuratorRetrievalQualityBenchmark {
    companion object {
        private const val TOP_K = 5
        private const val CANDIDATE_MULTIPLIER = 2
        private const val RECENCY_WEIGHT = 0.1f
        private const val IMPORTANCE_THRESHOLD = 0.3f

        private val qdrantContainer: GenericContainer<Nothing> by lazy {
            GenericContainer<Nothing>(DockerImageName.parse("qdrant/qdrant:v1.9.1"))
                .withExposedPorts(6333, 6334)
                .apply { start() }
        }

        @JvmStatic
        @AfterAll
        fun stopContainer() {
            if (qdrantContainer.isRunning) {
                qdrantContainer.stop()
            }
        }
    }

    @Test
    fun compareNoiseRatioBeforeAndAfterCuration() {
        val apiKey = System.getenv("OPENAI_API_KEY")
        assumeTrue(!apiKey.isNullOrBlank()) { "OPENAI_API_KEY 환경변수가 없어 벤치마크를 건너뜁니다" }

        val collectionName = "benchmark_memories_${UUID.randomUUID()}"
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
        // QdrantVectorStore는 InitializingBean이라 Spring 컨텍스트 밖에서 수동 생성하면
        // afterPropertiesSet()을 직접 호출해줘야 initializeSchema(true)가 실제로 컬렉션을 만든다.
        vectorStore.afterPropertiesSet()

        val properties = RagDialogueProperties().apply { qdrant.collectionName = collectionName }
        val embeddingPort = SpringAiEmbeddingAdapter(embeddingModel)
        val vectorMemoryPort = SpringAiVectorDbAdapter(vectorStore, qdrantClient, properties)
        val curatorPolicy = MemoryCuratorPolicy(0.05f, 0.1f, 0.9f, 0.1f, 90L)
        val curatorService = MemoryCuratorService(vectorMemoryPort, curatorPolicy, MemoryDecayService())

        val sessionId = ConversationSessionId.generate().value
        val query = "요즘 취미로 뭐 하고 지내?"

        // signal: 최근 접근, 실제 질의와 관련 있는 진짜 기억. 일부러 topK(5)보다 적게(3개) 둔다 -
        // 실제로도 초반 사용자는 진짜 최신 기억이 몇 개 안 되고, 검색은 topK를 채우려 하기 때문에
        // 부족한 슬롯을 무엇으로 채우는지가 큐레이터 유무의 차이를 만든다.
        val signalContents =
            listOf(
                "사용자는 매일 아침 6시에 조깅을 한다",
                "사용자는 다음 달 하프 마라톤 대회에 참가할 예정이다",
                "사용자는 최근 러닝화를 새로 구입했다",
            )
        // noise: signal과 같은 주제(조깅/마라톤/등산/요가)라 벡터 유사도만으로는 구분 안 되지만,
        // "과거에 하다가 그만둠"이라 지금은 사실이 아닌 기억. importance는 검색 필터(0.3) 통과할
        // 만큼 높고 90일 이상 미접근 - 감쇠/큐레이터 없이는 영구히 검색에 남아 최신 정보인 것처럼
        // 섞여 나오는 케이스(같은 주제라 임베딩만으로는 걸러지지 않는 게 핵심).
        val noiseContents =
            listOf(
                "사용자는 예전에 매일 아침 조깅을 했었지만 지금은 그만뒀다",
                "사용자는 몇 년 전 마라톤 대회를 준비하다가 그만뒀다",
                "사용자는 과거에 러닝화를 여러 켤레 사모았었지만 지금은 안 신는다",
                "사용자는 오래전 등산 동호회에 잠깐 다녔지만 지금은 안 나간다",
                "사용자는 예전에 요가 학원에 다녔었지만 지금은 그만뒀다",
            )

        val now = Instant.now()
        seedMemories(
            embeddingPort,
            vectorMemoryPort,
            sessionId,
            signalContents,
            importance = 0.6f,
            lastAccessedAt = now
        )
        seedMemories(
            embeddingPort,
            vectorMemoryPort,
            sessionId,
            noiseContents,
            importance = 0.5f,
            lastAccessedAt = now.minus(120, ChronoUnit.DAYS),
        )

        val beforeContents = topKContents(vectorMemoryPort, embeddingPort, sessionId, query)
        val noiseInBefore = beforeContents.count { it in noiseContents }

        curatorService.runDecayAndArchive().block()

        val afterContents = topKContents(vectorMemoryPort, embeddingPort, sessionId, query)
        val noiseInAfter = afterContents.count { it in noiseContents }

        println(
            """

            === 메모리 큐레이터 검색 품질 벤치마크 ===
            쿼리: "$query" (top-$TOP_K 요청, signal 원본 ${signalContents.size}개 / noise ${noiseContents.size}개)
            적용 전 - 반환 ${beforeContents.size}개 중 노이즈 혼입 $noiseInBefore, precision ${beforeContents.size - noiseInBefore}/${beforeContents.size}
              결과: $beforeContents
            적용 후 - 반환 ${afterContents.size}개 중 노이즈 혼입 $noiseInAfter, precision ${afterContents.size - noiseInAfter}/${afterContents.size}
              결과: $afterContents
            =========================================

            """.trimIndent(),
        )

        assertThat(noiseInAfter).isLessThanOrEqualTo(noiseInBefore)
    }

    /** MemoryRetrievalService와 동일한 랭킹 로직이되, access-boost 부작용(측정 자체가 lastAccessedAt을 갱신하는 문제) 없이 순수 조회만 수행한다. */
    private fun topKContents(
        vectorMemoryPort: VectorMemoryPort,
        embeddingPort: EmbeddingPort,
        sessionId: String,
        query: String,
    ): List<String> {
        val queryEmbedding = embeddingPort.embed(query).block()!!.vector
        val candidates =
            vectorMemoryPort
                .search(
                    sessionId,
                    queryEmbedding,
                    listOf(MemoryType.EXPERIENTIAL, MemoryType.FACTUAL),
                    IMPORTANCE_THRESHOLD,
                    TOP_K * CANDIDATE_MULTIPLIER,
                ).collectList()
                .block()!!
        return candidates
            .sortedByDescending { it.calculateRankedScore(RECENCY_WEIGHT) }
            .take(TOP_K)
            .map(Memory::content)
    }

    private fun seedMemories(
        embeddingPort: EmbeddingPort,
        vectorMemoryPort: VectorMemoryPort,
        sessionId: String,
        contents: List<String>,
        importance: Float,
        lastAccessedAt: Instant,
    ) {
        contents.forEach { content ->
            val memory =
                Memory(
                    id = null,
                    sessionId = sessionId,
                    type = MemoryType.EXPERIENTIAL,
                    content = content,
                    importance = importance,
                    createdAt = lastAccessedAt,
                    lastAccessedAt = lastAccessedAt,
                    accessCount = 1,
                )
            val embedding = embeddingPort.embed(content).block()!!
            vectorMemoryPort.upsert(memory, embedding.vector).block()
        }
    }
}
