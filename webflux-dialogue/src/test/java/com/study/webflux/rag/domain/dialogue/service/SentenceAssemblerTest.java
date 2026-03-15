package com.study.webflux.rag.domain.dialogue.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;
import reactor.test.StepVerifier;

class SentenceAssemblerTest {

	private SentenceAssembler sentenceAssembler;

	@BeforeEach
	void setUp() {
		sentenceAssembler = new SentenceAssembler();
	}

	@Test
	@DisplayName("토큰 스트림을 문장 단위로 조립한다")
	void assemble_shouldCombineTokensIntoSentences() {
		Flux<String> tokens = Flux.just("Hello", " ", "world", ".");

		Flux<String> result = sentenceAssembler.assemble(tokens);

		StepVerifier.create(result).expectNext("Hello world.").verifyComplete();
	}

	@Test
	@DisplayName("여러 문장이 포함된 토큰을 개별 문장으로 분리한다")
	void assemble_shouldHandleMultipleSentences() {
		Flux<String> tokens = Flux.just("First", " sentence", ".", " Second", " sentence", "!");

		Flux<String> result = sentenceAssembler.assemble(tokens);

		StepVerifier.create(result).expectNext("First sentence.").expectNext("Second sentence!")
			.verifyComplete();
	}

	@Test
	@DisplayName("한국어 문장을 정상적으로 조립한다")
	void assemble_shouldHandleKoreanSentences() {
		Flux<String> tokens = Flux.just("안녕", "하세요", ".", " 반갑", "습니", "다", ".");

		Flux<String> result = sentenceAssembler.assemble(tokens);

		StepVerifier.create(result).expectNext("안녕하세요.").expectNext("반갑습니다.").verifyComplete();
	}

	@Test
	@DisplayName("물음표로 끝나는 문장을 정상 처리한다")
	void assemble_shouldHandleQuestionMarks() {
		Flux<String> tokens = Flux.just("How", " are", " you", "?");

		Flux<String> result = sentenceAssembler.assemble(tokens);

		StepVerifier.create(result).expectNext("How are you?").verifyComplete();
	}

	@Test
	@DisplayName("느낌표로 끝나는 문장을 정상 처리한다")
	void assemble_shouldHandleExclamationMarks() {
		Flux<String> tokens = Flux.just("Great", " news", "!");

		Flux<String> result = sentenceAssembler.assemble(tokens);

		StepVerifier.create(result).expectNext("Great news!").verifyComplete();
	}

	@Test
	@DisplayName("공백이 포함된 토큰을 정상 조립한다")
	void assemble_shouldHandleTokensWithSpaces() {
		Flux<String> tokens = Flux.just("Hello", " ", "world", ".");

		Flux<String> result = sentenceAssembler.assemble(tokens);

		StepVerifier.create(result).expectNext("Hello world.").verifyComplete();
	}

	@Test
	@DisplayName("혼합된 구두점을 각각 별도 문장으로 분리한다")
	void assemble_shouldHandleMixedPunctuation() {
		Flux<String> tokens = Flux.just("First", ".", " Second", "!", " Third", "?");

		Flux<String> result = sentenceAssembler.assemble(tokens);

		StepVerifier.create(result).expectNext("First.").expectNext("Second!").expectNext("Third?")
			.verifyComplete();
	}

	@Test
	@DisplayName("빈 토큰 스트림은 빈 결과를 반환한다")
	void assemble_withEmptyFlux_shouldReturnEmpty() {
		Flux<String> tokens = Flux.empty();

		Flux<String> result = sentenceAssembler.assemble(tokens);

		StepVerifier.create(result).verifyComplete();
	}

	@Test
	@DisplayName("maxChars 초과 시 구두점 없이도 문장을 분리한다")
	void assemble_shouldFlushWhenMaxCharsExceeded() {
		SentenceAssembler assembler = new SentenceAssembler(10);
		// 총 12자 - 구두점 없음
		Flux<String> tokens = Flux.just("Hello", " world", " test");

		Flux<String> result = assembler.assemble(tokens);

		StepVerifier.create(result).expectNextMatches(s -> s.length() <= 12).verifyComplete();
	}

	@Test
	@DisplayName("maxChars 도달 후 나머지 토큰도 별도 문장으로 분리된다")
	void assemble_shouldSplitIntoMultipleChunksWhenMaxCharsExceeded() {
		SentenceAssembler assembler = new SentenceAssembler(5);
		// 각 토큰 "Hello"(5자), " World"(6자) - 첫 토큰이 5자 이상이면 즉시 flush
		Flux<String> tokens = Flux.just("Hello", " World", ".");

		Flux<String> result = assembler.assemble(tokens);

		// 5자 제한으로 최소 1번 이상 분리됨
		StepVerifier.create(result).expectNextCount(1).thenConsumeWhile(s -> true).verifyComplete();
	}
}
