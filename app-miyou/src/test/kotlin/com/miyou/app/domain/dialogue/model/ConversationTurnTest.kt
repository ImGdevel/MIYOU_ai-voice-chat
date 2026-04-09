package com.miyou.app.domain.dialogue.model

import com.miyou.app.fixture.ConversationSessionFixture
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import java.time.Instant

class ConversationTurnTest {
    @Test
    @DisplayName("유효한 질문으로 대화 턴을 생성한다")
    fun create_validQuery_success() {
        val sessionId = ConversationSessionFixture.createId()
        val query = "hello"

        val turn = ConversationTurn.create(sessionId, query)

        assertThat(turn.sessionId).isEqualTo(sessionId)
        assertThat(turn.query).isEqualTo(query)
        assertThat(turn.id).isNull()
        assertThat(turn.response).isNull()
        assertThat(turn.createdAt).isNotNull()
    }

    @Test
    @DisplayName("질문이 비어 있으면 대화 턴을 생성할 수 없다")
    fun create_blankQuery_throwsException() {
        val sessionId = ConversationSessionFixture.createId()

        assertThatThrownBy { ConversationTurn.create(sessionId, "   ") }
            .isInstanceOf(IllegalArgumentException::class.java)
            .hasMessageContaining("query cannot be null or blank")
    }

    @Test
    @DisplayName("응답을 포함한 복사본을 반환한다")
    fun withResponse_addsResponse() {
        val sessionId = ConversationSessionFixture.createId()
        val turn = ConversationTurn.create(sessionId, "question")
        val response = "answer"

        val updatedTurn = turn.withResponse(response)

        assertThat(updatedTurn.response).isEqualTo(response)
        assertThat(updatedTurn.query).isEqualTo(turn.query)
        assertThat(updatedTurn.createdAt).isEqualTo(turn.createdAt)
    }

    @Test
    @DisplayName("식별자를 포함한 모든 필드로 대화 턴을 생성한다")
    fun withId_createsWithAllFields() {
        val sessionId = ConversationSessionFixture.createId()
        val id = "test-id"
        val query = "question"
        val response = "answer"
        val now = Instant.now()

        val turn = ConversationTurn.withId(id, sessionId, query, response, now)

        assertThat(turn.id).isEqualTo(id)
        assertThat(turn.sessionId).isEqualTo(sessionId)
        assertThat(turn.query).isEqualTo(query)
        assertThat(turn.response).isEqualTo(response)
        assertThat(turn.createdAt).isEqualTo(now)
    }

    @Test
    @DisplayName("질문이 비어 있으면 식별자 기반 생성에 실패한다")
    fun withId_blankQuery_throwsException() {
        val sessionId = ConversationSessionFixture.createId()

        assertThatThrownBy {
            ConversationTurn.withId("id", sessionId, "   ", "response", Instant.now())
        }.isInstanceOf(IllegalArgumentException::class.java)
            .hasMessageContaining("query cannot be null or blank")
    }
}
