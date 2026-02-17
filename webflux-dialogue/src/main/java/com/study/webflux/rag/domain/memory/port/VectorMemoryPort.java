package com.study.webflux.rag.domain.memory.port;

import java.time.Instant;
import java.util.List;

import com.study.webflux.rag.domain.dialogue.model.PersonaId;
import com.study.webflux.rag.domain.dialogue.model.UserId;
import com.study.webflux.rag.domain.memory.model.Memory;
import com.study.webflux.rag.domain.memory.model.MemoryType;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * VectorMemoryPort는 벡터 메모리 저장소와 상호작용하는 역할을 담당합니다.
 */
public interface VectorMemoryPort {

	/**
	 * 메모리를 벡터 임베딩과 함께 저장하거나 업데이트합니다.
	 *
	 * @param memory
	 *            저장하거나 업데이트할 Memory 객체
	 * @param embedding
	 *            벡터 임베딩
	 * @return 저장되거나 업데이트된 Memory 객체
	 */
	Mono<Memory> upsert(Memory memory, List<Float> embedding);

	/**
	 * 벡터 임베딩을 기반으로 메모리를 검색합니다.
	 *
	 * @param personaId
	 *            페르소나 ID
	 * @param userId
	 *            사용자 ID
	 * @param queryEmbedding
	 *            검색 쿼리의 벡터 임베딩
	 * @param types
	 *            검색할 메모리 타입 목록
	 * @param importanceThreshold
	 *            중요도 임계값
	 * @param topK
	 *            검색할 상위 메모리 개수
	 * @return 검색된 메모리 항목들의 Flux 스트림
	 */
	Flux<Memory> search(PersonaId personaId,
		UserId userId,
		List<Float> queryEmbedding,
		List<MemoryType> types,
		float importanceThreshold,
		int topK);

	default Flux<Memory> search(UserId userId,
		List<Float> queryEmbedding,
		List<MemoryType> types,
		float importanceThreshold,
		int topK) {
		return search(PersonaId
			.defaultPersona(), userId, queryEmbedding, types, importanceThreshold, topK);
	}

	/**
	 * 메모리의 중요도를 업데이트합니다.
	 *
	 * @param memoryId
	 *            메모리 ID
	 * @param newImportance
	 *            새로운 중요도 값
	 * @param lastAccessedAt
	 *            마지막 접근 시간
	 * @param accessCount
	 *            접근 횟수
	 * @return 완료 신호를 나타내는 Mono<Void>
	 */
	Mono<Void> updateImportance(String memoryId,
		float newImportance,
		Instant lastAccessedAt,
		int accessCount);
}
