package com.miyou.app.infrastructure.retrieval.adapter

import com.miyou.app.domain.dialogue.model.ConversationTurn
import com.miyou.app.domain.dialogue.port.ConversationRepository
import com.miyou.app.fixture.ConversationSessionFixture
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.ArgumentMatchers.anyInt
import org.mockito.ArgumentMatchers.eq
import org.mockito.Mock
import org.mockito.Mockito.`when`
import org.mockito.junit.jupiter.MockitoExtension
import reactor.core.publisher.Flux
import reactor.test.StepVerifier
import java.time.Instant

@ExtendWith(MockitoExtension::class)
class InMemoryRetrievalAdapterTest {
    @Mock
    private lateinit var conversationRepository: ConversationRepository

    private lateinit var adapter: InMemoryRetrievalAdapter

    @BeforeEach
    fun setUp() {
        adapter = InMemoryRetrievalAdapter(conversationRepository)
    }

    @Test
    @DisplayName("키워드가 겹치는 대화만 검색 결과에 포함한다")
    fun retrieve_keywordMatch_success() {
        val sessionId = ConversationSessionFixture.createId()
        val turns =
            listOf(
                ConversationTurn.withId("id-1", sessionId, "spring boot 공부", "좋아", Instant.now()),
                ConversationTurn.withId("id-2", sessionId, "파이썬 배우기", "재밌다", Instant.now()),
                ConversationTurn.withId("id-3", sessionId, "spring 설정 정리", "ok", Instant.now()),
            )
        `when`(conversationRepository.findRecent(eq(sessionId), anyInt())).thenReturn(Flux.fromIterable(turns))

        StepVerifier
            .create(adapter.retrieve(sessionId, "spring boot tips", 5))
            .assertNext { context ->
                assertThat(context.documents()).hasSize(2)
                assertThat(context.documents()[0].content()).isEqualTo("spring boot 공부")
                assertThat(context.documents()[1].content()).isEqualTo("spring 설정 정리")
            }.verifyComplete()
    }

    @Test
    @DisplayName("대화가 없으면 빈 결과를 반환한다")
    fun retrieve_empty_conversations() {
        val sessionId = ConversationSessionFixture.createId()
        `when`(conversationRepository.findRecent(eq(sessionId), anyInt())).thenReturn(Flux.empty())

        StepVerifier
            .create(adapter.retrieve(sessionId, "test", 5))
            .assertNext { context -> assertThat(context.isEmpty()).isTrue() }
            .verifyComplete()
    }

    @Test
    @DisplayName("메모리 검색은 빈 결과를 반환한다")
    fun retrieveMemories_returnsEmpty() {
        val sessionId = ConversationSessionFixture.createId()

        StepVerifier
            .create(adapter.retrieveMemories(sessionId, "test", 5))
            .assertNext { result -> assertThat(result.isEmpty()).isTrue() }
            .verifyComplete()
    }

    @Test
    @DisplayName("저장소 오류를 그대로 전파한다")
    fun retrieve_error_propagates() {
        val sessionId = ConversationSessionFixture.createId()
        `when`(conversationRepository.findRecent(eq(sessionId), anyInt()))
            .thenReturn(Flux.error(RuntimeException("DB error")))

        StepVerifier
            .create(adapter.retrieve(sessionId, "test", 5))
            .expectErrorMatches { throwable -> throwable.message!!.contains("DB error") }
            .verify()
    }
}
