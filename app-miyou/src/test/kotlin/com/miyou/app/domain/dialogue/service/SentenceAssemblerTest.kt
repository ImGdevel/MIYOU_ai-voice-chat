package com.miyou.app.domain.dialogue.service

import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import reactor.core.publisher.Flux
import reactor.test.StepVerifier

class SentenceAssemblerTest {
    private lateinit var sentenceAssembler: SentenceAssembler

    @BeforeEach
    fun setUp() {
        sentenceAssembler = SentenceAssembler()
    }

    @Test
    @DisplayName("토큰 스트림을 문장 단위로 조립한다")
    fun assemble_shouldCombineTokensIntoSentences() {
        val tokens = Flux.just("Hello", " ", "world", ".")

        val result = sentenceAssembler.assemble(tokens)

        StepVerifier
            .create(result)
            .expectNext("Hello world.")
            .verifyComplete()
    }

    @Test
    @DisplayName("여러 문장이 포함된 토큰은 문장별로 분리된다")
    fun assemble_shouldHandleMultipleSentences() {
        val tokens = Flux.just("First", " sentence", ".", " Second", " sentence", "!")

        val result = sentenceAssembler.assemble(tokens)

        StepVerifier
            .create(result)
            .expectNext("First sentence.")
            .expectNext("Second sentence!")
            .verifyComplete()
    }

    @Test
    @DisplayName("한국어 문장도 정상적으로 조립한다")
    fun assemble_shouldHandleKoreanSentences() {
        val tokens = Flux.just("안녕", "하세요", ".", " 반갑", "습니다", ".")

        val result = sentenceAssembler.assemble(tokens)

        StepVerifier
            .create(result)
            .expectNext("안녕하세요.")
            .expectNext("반갑습니다.")
            .verifyComplete()
    }

    @Test
    @DisplayName("물음표로 끝나는 문장도 정상 처리한다")
    fun assemble_shouldHandleQuestionMarks() {
        val tokens = Flux.just("How", " are", " you", "?")

        val result = sentenceAssembler.assemble(tokens)

        StepVerifier
            .create(result)
            .expectNext("How are you?")
            .verifyComplete()
    }

    @Test
    @DisplayName("느낌표로 끝나는 문장도 정상 처리한다")
    fun assemble_shouldHandleExclamationMarks() {
        val tokens = Flux.just("Great", " news", "!")

        val result = sentenceAssembler.assemble(tokens)

        StepVerifier
            .create(result)
            .expectNext("Great news!")
            .verifyComplete()
    }

    @Test
    @DisplayName("공백 토큰을 포함해도 정상 조립한다")
    fun assemble_shouldHandleTokensWithSpaces() {
        val tokens = Flux.just("Hello", " ", "world", ".")

        val result = sentenceAssembler.assemble(tokens)

        StepVerifier
            .create(result)
            .expectNext("Hello world.")
            .verifyComplete()
    }

    @Test
    @DisplayName("혼합된 구두점은 각각 별도 문장으로 분리된다")
    fun assemble_shouldHandleMixedPunctuation() {
        val tokens = Flux.just("First", ".", " Second", "!", " Third", "?")

        val result = sentenceAssembler.assemble(tokens)

        StepVerifier
            .create(result)
            .expectNext("First.")
            .expectNext("Second!")
            .expectNext("Third?")
            .verifyComplete()
    }

    @Test
    @DisplayName("빈 토큰 스트림은 빈 결과를 반환한다")
    fun assemble_withEmptyFlux_shouldReturnEmpty() {
        val tokens = Flux.empty<String>()

        val result = sentenceAssembler.assemble(tokens)

        StepVerifier
            .create(result)
            .verifyComplete()
    }
}
