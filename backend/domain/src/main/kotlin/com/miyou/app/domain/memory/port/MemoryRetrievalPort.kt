package com.miyou.app.domain.memory.port

import com.miyou.app.domain.memory.model.MemoryRetrievalResult
import reactor.core.publisher.Mono

/**
 * 대화 중 질문에 연관된 기억을 데이터베이스 및 검색 엔진으로부터 탐색하여 반환하는 포트.
 */
interface MemoryRetrievalPort {
    /**
     * 지정된 세션의 질문과 관련성이 높은 경험적/사실적 기억들을 찾아 반환합니다.
     *
     * @param sessionId 대화 세션 ID
     * @param query 검색할 질문 텍스트
     * @param topK 반환할 최대 기억 수
     * @return 검색된 기억들을 분류한 [MemoryRetrievalResult] (Mono)
     */
    fun retrieveMemories(
        sessionId: String,
        query: String,
        topK: Int,
    ): Mono<MemoryRetrievalResult>
}
