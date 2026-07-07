package com.miyou.app.domain.memory.port

import com.miyou.app.domain.memory.model.Memory
import com.miyou.app.domain.memory.model.MemoryType
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import java.time.Instant

/**
 * 기억(Memory)을 임베딩 벡터 데이터와 함께 저장하고 검색 및 관리하는 벡터 데이터베이스 연동 포트.
 */
interface VectorMemoryPort {
    /**
     * 새로운 기억을 벡터 및 메타데이터 저장소에 삽입하거나 기존 기억을 덮어씁니다.
     *
     * @param memory 저장할 기억 모델 객체
     * @param embedding 기억 내용의 수치 벡터 리스트
     * @return 저장 성공 시 생성/갱신된 [Memory] 객체 (Mono)
     */
    fun upsert(
        memory: Memory,
        embedding: List<Float>,
    ): Mono<Memory>

    /**
     * 특정 대화 세션에 소속된 기억 후보군을 임베딩 벡터 유사도를 기준으로 검색합니다.
     *
     * @param sessionId 대화 세션 ID
     * @param queryEmbedding 검색 질의의 임베딩 벡터 리스트
     * @param types 검색할 기억 타입 목록 (경험적, 사실적 등)
     * @param importanceThreshold 검색할 최소 중요도 기준치
     * @param topK 반환할 최대 결과 개수
     * @return 검색 결과에 해당하는 [Memory] 객체 스트림 (Flux)
     */
    fun search(
        sessionId: String,
        queryEmbedding: List<Float>,
        types: List<MemoryType>,
        importanceThreshold: Float,
        topK: Int,
    ): Flux<Memory>

    /**
     * 기억에 대한 메트릭(중요도 가중치, 마지막 접근 시점, 누적 접근 횟수)을 부분 업데이트합니다.
     *
     * @param memoryId 업데이트할 기억 ID
     * @param newImportance 갱신할 중요도 점수
     * @param lastAccessedAt 마지막 접근 일시
     * @param accessCount 누적 접근 횟수
     * @return 업데이트 완료 상태 (Mono)
     */
    fun updateImportance(
        memoryId: String,
        newImportance: Float,
        lastAccessedAt: Instant,
        accessCount: Int,
    ): Mono<Void>

    /** 아직 아카이브되지 않은(archivedAt이 없는) 메모리를 batchSize 단위로 페이지네이션하며 스트리밍한다. */
    fun findAllActive(batchSize: Int): Flux<Memory>

    /** 큐레이터 배치가 계산한 감쇠된 importance / (필요 시) archivedAt을 저장소에 반영한다. */
    fun applyDecayAndArchive(memory: Memory): Mono<Void>
}
