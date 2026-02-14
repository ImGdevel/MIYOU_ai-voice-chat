package com.study.webflux.rag.domain.retrieval.port;

import com.study.webflux.rag.domain.dialogue.model.UserId;
import com.study.webflux.rag.domain.memory.model.MemoryRetrievalResult;
import com.study.webflux.rag.domain.retrieval.model.RetrievalContext;
import reactor.core.publisher.Mono;

/**
 * 질의 기반 문서 검색과 장기 메모리 검색을 제공하는 도메인 포트입니다.
 */
public interface RetrievalPort {

	/**
	 * 주어진 쿼리에 대해 상위 K개의 관련 문서를 검색합니다.
	 *
	 * @param query
	 *            검색 쿼리
	 * @param topK
	 *            검색할 상위 문서 수
	 * @return 검색 결과 컨텍스트
	 */
	Mono<RetrievalContext> retrieve(UserId userId, String query, int topK);

	/**
	 * 벡터 메모리에서 상위 K개의 관련 항목을 검색합니다.
	 *
	 * @param query
	 *            검색 쿼리
	 * @param topK
	 *            검색할 상위 메모리 수
	 * @return 검색된 메모리 결과
	 */
	Mono<MemoryRetrievalResult> retrieveMemories(UserId userId, String query, int topK);
}
