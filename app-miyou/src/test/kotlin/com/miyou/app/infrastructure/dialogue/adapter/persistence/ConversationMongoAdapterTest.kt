package com.miyou.app.infrastructure.dialogue.adapter.persistence

import com.miyou.app.domain.dialogue.model.ConversationTurn
import com.miyou.app.fixture.ConversationSessionFixture
import com.miyou.app.infrastructure.dialogue.adapter.persistence.document.ConversationDocument
import com.miyou.app.infrastructure.dialogue.repository.ConversationMongoRepository
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.ArgumentMatchers.any
import org.mockito.Mock
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
import org.mockito.junit.jupiter.MockitoExtension
import org.springframework.data.domain.PageRequest
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import reactor.test.StepVerifier
import java.time.Instant

@ExtendWith(MockitoExtension::class)
class ConversationMongoAdapterTest {
    @Mock
    private lateinit var mongoRepository: ConversationMongoRepository

    private lateinit var adapter: ConversationMongoAdapter

    @BeforeEach
    fun setUp() {
        adapter = ConversationMongoAdapter(mongoRepository)
    }

    @Test
    @DisplayName("대화를 저장하고 도메인 객체로 반환한다")
    fun save_success() {
        val now = Instant.now()
        val sessionId = ConversationSessionFixture.createId()
        val turn = ConversationTurn.create(sessionId, "안녕하세요")
        val savedDocument = ConversationDocument("id-123", sessionId.value(), "안녕하세요", null, now)

        `when`(mongoRepository.save(any(ConversationDocument::class.java))).thenReturn(Mono.just(savedDocument))

        StepVerifier
            .create(adapter.save(turn))
            .assertNext { result ->
                assertThat(result.id()).isEqualTo("id-123")
                assertThat(result.sessionId()).isEqualTo(sessionId)
                assertThat(result.query()).isEqualTo("안녕하세요")
            }.verifyComplete()

        verify(mongoRepository).save(any(ConversationDocument::class.java))
    }

    @Test
    @DisplayName("최신 대화를 오래된 순서로 반환한다")
    fun findRecent_success() {
        val sessionId = ConversationSessionFixture.createId()
        val documents =
            listOf(
                ConversationDocument("id-3", sessionId.value(), "세 번째", "응답3", Instant.parse("2025-01-01T12:00:00Z")),
                ConversationDocument("id-2", sessionId.value(), "두 번째", "응답2", Instant.parse("2025-01-01T11:00:00Z")),
                ConversationDocument("id-1", sessionId.value(), "첫 번째", "응답1", Instant.parse("2025-01-01T10:00:00Z")),
            )
        `when`(mongoRepository.findBySessionIdOrderByCreatedAtDesc(sessionId.value(), PageRequest.of(0, 10)))
            .thenReturn(Flux.fromIterable(documents))

        StepVerifier
            .create(adapter.findRecent(sessionId, 10))
            .assertNext { result -> assertThat(result.id()).isEqualTo("id-1") }
            .assertNext { result -> assertThat(result.id()).isEqualTo("id-2") }
            .assertNext { result -> assertThat(result.id()).isEqualTo("id-3") }
            .verifyComplete()
    }

    @Test
    @DisplayName("조회 결과가 없으면 빈 Flux를 반환한다")
    fun findRecent_empty() {
        val sessionId = ConversationSessionFixture.createId()
        `when`(mongoRepository.findBySessionIdOrderByCreatedAtDesc(sessionId.value(), PageRequest.of(0, 10)))
            .thenReturn(Flux.empty())

        StepVerifier
            .create(adapter.findRecent(sessionId, 10))
            .verifyComplete()
    }

    @Test
    @DisplayName("저장 오류를 그대로 전파한다")
    fun save_error_propagates() {
        val sessionId = ConversationSessionFixture.createId()
        val turn = ConversationTurn.create(sessionId, "에러 테스트")
        `when`(mongoRepository.save(any(ConversationDocument::class.java)))
            .thenReturn(Mono.error(RuntimeException("MongoDB connection failed")))

        StepVerifier
            .create(adapter.save(turn))
            .expectErrorMatches { throwable -> throwable.message!!.contains("MongoDB connection failed") }
            .verify()
    }
}
