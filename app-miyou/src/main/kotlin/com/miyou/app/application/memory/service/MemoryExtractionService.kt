package com.miyou.app.application.memory.service

import com.miyou.app.application.monitoring.port.MemoryExtractionMetricsPort
import com.miyou.app.domain.dialogue.model.ConversationSessionId
import com.miyou.app.domain.dialogue.model.ConversationTurn
import com.miyou.app.domain.dialogue.port.ConversationRepository
import com.miyou.app.domain.memory.model.ExtractedMemory
import com.miyou.app.domain.memory.model.Memory
import com.miyou.app.domain.memory.model.MemoryExtractionContext
import com.miyou.app.domain.memory.port.ConversationCounterPort
import com.miyou.app.domain.memory.port.EmbeddingPort
import com.miyou.app.domain.memory.port.MemoryExtractionPort
import com.miyou.app.domain.memory.port.VectorMemoryPort
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.stereotype.Service
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import java.time.Instant

/**
 * 메모리 추출 서비스.
 *
 * 주기적으로 대화 이력에서 의미있는 정보(인물, 선호도, 사건 등)를 추출하여 벡터 저장소에 저장.
 * 사용자 선호도 학습 및 개인화 맥락 강화 목적.
 */
@Service
class MemoryExtractionService(
    private val conversationRepository: ConversationRepository,
    private val counterPort: ConversationCounterPort,
    private val extractionPort: MemoryExtractionPort,
    private val embeddingPort: EmbeddingPort,
    private val vectorMemoryPort: VectorMemoryPort,
    private val retrievalService: MemoryRetrievalService,
    private val extractionMetrics: MemoryExtractionMetricsPort,
    private val conversationThreshold: Int,
) {
    private val logger = KotlinLogging.logger {}

    /**
     * 주기 도달 시 메모리 추출 수행.
     * 임계값(conversationThreshold) 도달마다 트리거됨.
     *
     * @param sessionId 대화 세션 ID
     * @return 추출 완료 (실패해도 무시)
     */
    fun checkAndExtract(sessionId: ConversationSessionId): Mono<Void> =
        counterPort
            .get(sessionId)
            .filter(::isExtractionTurn)
            .flatMap { count ->
                logger.info { "메모리 추출 트리거 sessionId=${sessionId.value}, count=$count" }
                extractionMetrics.recordExtractionTriggered()
                performExtraction(sessionId)
            }.then()

    private fun performExtraction(sessionId: ConversationSessionId): Mono<Void> =
        loadRecentConversations(sessionId)
            .flatMap { conversations ->
                buildExtractionContext(sessionId, conversations)
            }.flatMap { context -> extractAndSave(sessionId, context) }

    private fun extractAndSave(
        sessionId: ConversationSessionId,
        context: MemoryExtractionContext,
    ): Mono<Void> =
        extractionPort
            .extractMemories(context)
            .collectList()
            .doOnNext { extractedList ->
                extractionMetrics.recordExtractionSuccess(extractedList.size)

                val typeCounts =
                    extractedList
                        .groupingBy(ExtractedMemory::type)
                        .eachCount()

                typeCounts.forEach { (type, count) ->
                    extractionMetrics.recordExtractedMemoryType(type.name, count)
                }

                extractedList.forEach { extracted ->
                    extractionMetrics.recordExtractedImportance(extracted.importance.toDouble())
                    logger.info {
                        "메모리 추출 근거 sessionId=${sessionId.value}, type=${extracted.type}, " +
                            "importance=${extracted.importance}, reasoning=${extracted.reasoning.replace("\n", "\\n")}"
                    }
                }
            }.doOnError { error ->
                extractionMetrics.recordExtractionFailure()
                logger.error(error) { "메모리 추출 실패" }
            }.flatMapMany(Flux<ExtractedMemory>::fromIterable)
            .flatMap { extracted -> saveExtractedMemory(extracted, context.existingMemories) }
            .doOnNext { memory ->
                logger.info {
                    "추출된 메모리 저장 완료 type=${memory.type}, importance=${memory.importance}, content=${memory.content}"
                }
            }.then()

    private fun saveExtractedMemory(
        extracted: ExtractedMemory,
        existingMemories: List<Memory>,
    ): Mono<Memory> {
        val memory = extracted.toMemory()

        return embeddingPort
            .embed(memory.content)
            .flatMap { embedding ->
                vectorMemoryPort.upsert(memory, embedding.vector)
            }.flatMap { saved -> archiveSupersededMemory(extracted, existingMemories).thenReturn(saved) }
    }

    /** 모순된 기존 메모리를 즉시 소프트 아카이브 - 큐레이터가 이미 쓰는 archive 경로를 그대로 재사용한다. */
    private fun archiveSupersededMemory(
        extracted: ExtractedMemory,
        existingMemories: List<Memory>,
    ): Mono<Void> {
        val targetId = extracted.supersedesMemoryId ?: return Mono.empty()
        val target = existingMemories.firstOrNull { it.id == targetId } ?: return Mono.empty()
        return vectorMemoryPort
            .applyDecayAndArchive(target.archive(Instant.now()))
            .doOnSuccess {
                logger.info { "모순 감지로 기존 메모리 아카이브 id=$targetId, 대체 내용=${extracted.content}" }
            }
    }

    private fun isExtractionTurn(count: Long): Boolean = count > 0 && count % conversationThreshold == 0L

    private fun loadRecentConversations(sessionId: ConversationSessionId): Mono<List<ConversationTurn>> =
        conversationRepository
            .findRecent(sessionId, conversationThreshold)
            .collectList()

    private fun buildExtractionContext(
        sessionId: ConversationSessionId,
        conversations: List<ConversationTurn>,
    ): Mono<MemoryExtractionContext> {
        val combinedQuery = mergeQueries(conversations)
        return retrievalService
            .retrieveMemories(sessionId, combinedQuery, 10)
            .map { result ->
                MemoryExtractionContext.of(sessionId, conversations, result.allMemories())
            }
    }

    private fun mergeQueries(conversations: List<ConversationTurn>): String =
        conversations.joinToString(" ") { it.query }
}
