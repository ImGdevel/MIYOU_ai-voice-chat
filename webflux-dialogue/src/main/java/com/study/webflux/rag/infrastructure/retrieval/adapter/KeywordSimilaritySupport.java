package com.study.webflux.rag.infrastructure.retrieval.adapter;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

import com.study.webflux.rag.domain.dialogue.model.ConversationTurn;
import com.study.webflux.rag.domain.retrieval.model.RetrievalDocument;

/**
 * 키워드 교집합 기반 유사도 계산과 상위 문서 추출을 위한 공통 유틸리티입니다.
 */
final class KeywordSimilaritySupport {

	private KeywordSimilaritySupport() {
	}

	/**
	 * 대화 턴 목록에서 query와의 유사도 점수를 계산해 관련도 순으로 정렬된 상위 문서를 반환합니다.
	 */
	static List<RetrievalDocument> rankDocumentsByQuery(String query,
		List<ConversationTurn> turns,
		int topK) {
		List<RetrievalDocument> sorted = turns.stream()
			.map(turn -> RetrievalDocument.of(turn.query(),
				scoreByTokenIntersection(query,
					turn.query())))
			.filter(doc -> doc.score().isRelevant())
			.sorted((a, b) -> Integer.compare(b.score().value(), a.score().value()))
			.toList();

		return sorted.size() > topK ? sorted.subList(0, topK) : sorted;
	}

	/**
	 * 공백 단위 토큰 교집합 크기를 유사도 점수로 계산합니다.
	 */
	static int scoreByTokenIntersection(String query, String candidate) {
		Set<String> queryWords = tokenize(query);
		Set<String> candidateWords = tokenize(candidate);

		Set<String> intersection = new HashSet<>(queryWords);
		intersection.retainAll(candidateWords);
		return intersection.size();
	}

	private static Set<String> tokenize(String text) {
		if (text == null || text.isBlank()) {
			return Set.of();
		}
		return Arrays.stream(text.toLowerCase(Locale.ROOT).split("\\s+"))
			.filter(word -> !word.isEmpty())
			.collect(Collectors.toSet());
	}
}
