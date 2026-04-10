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
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono

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
    private val logger = LoggerFactory.getLogger(javaClass)

    fun checkAndExtract(sessionId: ConversationSessionId): Mono<Void> =
        counterPort
            .get(sessionId)
            .filter(::isExtractionTurn)
            .flatMap { count ->
                logger.info("메모리 추출 트리거 sessionId={}, count={}", sessionId.value, count)
                extractionMetrics.recordExtractionTriggered()
                performExtraction(sessionId)
            }.then()

    private fun performExtraction(sessionId: ConversationSessionId): Mono<Void> =
        loadRecentConversations(sessionId)
            .flatMap { conversations ->
                buildExtractionContext(sessionId, conversations)
            }.flatMapMany(extractionPort::extractMemories)
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
                }
            }.doOnError { error ->
                extractionMetrics.recordExtractionFailure()
                logger.error("메모리 추출 실패", error)
            }.flatMapMany(Flux<ExtractedMemory>::fromIterable)
            .flatMap(this::saveExtractedMemory)
            .doOnNext { memory ->
                logger.info(
                    "추출된 메모리 저장 완료 type={}, importance={}, content={}",
                    memory.type,
                    memory.importance,
                    memory.content,
                )
            }.then()

    private fun saveExtractedMemory(extracted: ExtractedMemory): Mono<Memory> {
        val memory = extracted.toMemory()

        return embeddingPort
            .embed(memory.content)
            .flatMap { embedding ->
                vectorMemoryPort.upsert(memory, embedding.vector)
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
