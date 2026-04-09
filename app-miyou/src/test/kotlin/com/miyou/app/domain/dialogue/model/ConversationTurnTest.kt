package com.miyou.app.domain.dialogue.model

import com.miyou.app.fixture.ConversationSessionFixture
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import java.time.Instant

class ConversationTurnTest {
    @Test
    @DisplayName("유효한 쿼리로 ConversationTurn 생성 성공")
    fun create_validQuery_success() {
        val sessionId = ConversationSessionFixture.createId()
        val query = "안녕하세요"

        val turn = ConversationTurn.create(sessionId, query)

        assertThat(turn.sessionId()).isEqualTo(sessionId)
        assertThat(turn.query()).isEqualTo(query)
        assertThat(turn.id()).isNull()
        assertThat(turn.response()).isNull()
        assertThat(turn.createdAt()).isNotNull()
    }

    @Test
    @DisplayName("null 쿼리로 생성 시 예외 발생")
    fun create_nullQuery_throwsException() {
        val sessionId = ConversationSessionFixture.createId()

        assertThatThrownBy { ConversationTurn.create(sessionId, null) }
            .isInstanceOf(IllegalArgumentException::class.java)
            .hasMessageContaining("query cannot be null or blank")
    }

    @Test
    @DisplayName("빈 문자열 쿼리로 생성 시 예외 발생")
    fun create_blankQuery_throwsException() {
        val sessionId = ConversationSessionFixture.createId()

        assertThatThrownBy { ConversationTurn.create(sessionId, "   ") }
            .isInstanceOf(IllegalArgumentException::class.java)
            .hasMessageContaining("query cannot be null or blank")
    }

    @Test
    @DisplayName("응답 추가")
    fun withResponse_addsResponse() {
        val sessionId = ConversationSessionFixture.createId()
        val turn = ConversationTurn.create(sessionId, "질문")
        val response = "응답입니다"

        val updatedTurn = turn.withResponse(response)

        assertThat(updatedTurn.response()).isEqualTo(response)
        assertThat(updatedTurn.query()).isEqualTo(turn.query())
        assertThat(updatedTurn.createdAt()).isEqualTo(turn.createdAt())
    }

    @Test
    @DisplayName("ID가 있는 완전한 ConversationTurn 생성")
    fun withId_createsWithAllFields() {
        val sessionId = ConversationSessionFixture.createId()
        val id = "test-id"
        val query = "질문"
        val response = "응답"
        val now = Instant.now()

        val turn = ConversationTurn.withId(id, sessionId, query, response, now)

        assertThat(turn.id()).isEqualTo(id)
        assertThat(turn.sessionId()).isEqualTo(sessionId)
        assertThat(turn.query()).isEqualTo(query)
        assertThat(turn.response()).isEqualTo(response)
        assertThat(turn.createdAt()).isEqualTo(now)
    }

    @Test
    @DisplayName("withId로 null 쿼리 생성 시 예외 발생")
    fun withId_nullQuery_throwsException() {
        val sessionId = ConversationSessionFixture.createId()

        assertThatThrownBy {
            ConversationTurn.withId("id", sessionId, null, "response", Instant.now())
        }.isInstanceOf(IllegalArgumentException::class.java)
            .hasMessageContaining("query cannot be null or blank")
    }
}
