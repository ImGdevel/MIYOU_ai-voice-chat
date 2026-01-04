package com.study.webflux.rag.domain.retrieval.port;

import com.study.webflux.rag.domain.memory.model.MemoryRetrievalResult;
import com.study.webflux.rag.domain.retrieval.model.RetrievalContext;
import reactor.core.publisher.Mono;

/**
 * RetrievalPort는 외부 벡터 데이터베이스나 검색 시스템과 상호작용하여 질의에 대한 관련 문서나 정보를 검색하는 역할을 담당합니다.
 */
public interface RetrievalPort {

	/**
	 * 주어진 쿼리에 대해 상위 K개의 관련 문서를 검색합니다.
	 *
	 * @param query
	 *            검색 쿼리
	 * @param topK
	 *            검색할 상위 문서의 개수
	 * @return 검색된 문서들을 포함하는 RetrievalContext 객체
	 */
	Mono<RetrievalContext> retrieve(String query, int topK);

	/**
	 * 메모리에서 상위 K개의 관련 항목을 검색합니다.
	 *
	 * @param query
	 *            검색 쿼리
	 * @param topK
	 *            검색할 상위 항목의 개수
	 * @return 검색된 메모리 항목들을 포함하는 MemoryRetrievalResult 객체
	 */
	Mono<MemoryRetrievalResult> retrieveMemories(String query, int topK);
}
