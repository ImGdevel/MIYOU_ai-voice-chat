package com.study.webflux.rag.infrastructure.retrieval.adapter;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Component;

import com.study.webflux.rag.domain.dialogue.port.ConversationRepository;
import com.study.webflux.rag.domain.memory.model.MemoryRetrievalResult;
import com.study.webflux.rag.domain.retrieval.model.RetrievalContext;
import com.study.webflux.rag.domain.retrieval.model.RetrievalDocument;
import com.study.webflux.rag.domain.retrieval.port.RetrievalPort;
import reactor.core.publisher.Mono;

@Component
@RequiredArgsConstructor
public class InMemoryRetrievalAdapter implements RetrievalPort {

	private final ConversationRepository conversationRepository;

	@Override
	public Mono<RetrievalContext> retrieve(String query, int topK) {
		return conversationRepository.findAll().collectList().map(turns -> {
			var sorted = turns.stream().map(turn -> {
				int score = calculateSimilarity(query, turn.query());
				return RetrievalDocument.of(turn.query(), score);
			}).filter(doc -> doc.score().isRelevant())
				.sorted((a, b) -> Integer.compare(b.score().value(), a.score().value()))
				.toList();

			List<RetrievalDocument> documents = sorted.size() > topK
				? sorted.subList(0, topK)
				: sorted;
			return RetrievalContext.of(query, documents);
		});
	}

	@Override
	public Mono<MemoryRetrievalResult> retrieveMemories(String query, int topK) {
		return Mono.just(MemoryRetrievalResult.empty());
	}

	private int calculateSimilarity(String query, String candidate) {
		Set<String> queryWords = tokenize(query);
		Set<String> candidateWords = tokenize(candidate);

		Set<String> intersection = new HashSet<>(queryWords);
		intersection.retainAll(candidateWords);

		return intersection.size();
	}

	private Set<String> tokenize(String text) {
		if (text == null || text.isBlank()) {
			return Set.of();
		}
		return Arrays.stream(text.toLowerCase().split("\\s+")).filter(word -> !word.isEmpty())
			.collect(Collectors.toSet());
	}
}
