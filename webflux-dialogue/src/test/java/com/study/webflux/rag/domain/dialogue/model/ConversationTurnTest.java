package com.study.webflux.rag.domain.dialogue.model;

import java.time.Instant;

import com.study.webflux.rag.fixture.ConversationSessionFixture;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ConversationTurnTest {

	@Test
	@DisplayName("유효한 쿼리로 ConversationTurn 생성 성공")
	void create_validQuery_success() {
		ConversationSessionId sessionId = ConversationSessionFixture.createId();
		String query = "안녕하세요";

		ConversationTurn turn = ConversationTurn.create(sessionId, query);

		assertThat(turn.sessionId()).isEqualTo(sessionId);
		assertThat(turn.query()).isEqualTo(query);
		assertThat(turn.id()).isNull();
		assertThat(turn.response()).isNull();
		assertThat(turn.createdAt()).isNotNull();
	}

	@Test
	@DisplayName("null 쿼리로 생성 시 예외 발생")
	void create_nullQuery_throwsException() {
		ConversationSessionId sessionId = ConversationSessionFixture.createId();
		assertThatThrownBy(() -> ConversationTurn.create(sessionId, null))
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("query cannot be null or blank");
	}

	@Test
	@DisplayName("빈 문자열 쿼리로 생성 시 예외 발생")
	void create_blankQuery_throwsException() {
		ConversationSessionId sessionId = ConversationSessionFixture.createId();
		assertThatThrownBy(() -> ConversationTurn.create(sessionId, "   "))
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("query cannot be null or blank");
	}

	@Test
	@DisplayName("응답 추가")
	void withResponse_addsResponse() {
		ConversationSessionId sessionId = ConversationSessionFixture.createId();
		ConversationTurn turn = ConversationTurn.create(sessionId, "질문");
		String response = "답변입니다";

		ConversationTurn updatedTurn = turn.withResponse(response);

		assertThat(updatedTurn.response()).isEqualTo(response);
		assertThat(updatedTurn.query()).isEqualTo(turn.query());
		assertThat(updatedTurn.createdAt()).isEqualTo(turn.createdAt());
	}

	@Test
	@DisplayName("ID와 함께 ConversationTurn 생성")
	void withId_createsWithAllFields() {
		ConversationSessionId sessionId = ConversationSessionFixture.createId();
		String id = "test-id";
		String query = "질문";
		String response = "답변";
		Instant now = Instant.now();

		ConversationTurn turn = ConversationTurn.withId(id, sessionId, query, response, now);

		assertThat(turn.id()).isEqualTo(id);
		assertThat(turn.sessionId()).isEqualTo(sessionId);
		assertThat(turn.query()).isEqualTo(query);
		assertThat(turn.response()).isEqualTo(response);
		assertThat(turn.createdAt()).isEqualTo(now);
	}

	@Test
	@DisplayName("withId로 null 쿼리 생성 시 예외 발생")
	void withId_nullQuery_throwsException() {
		ConversationSessionId sessionId = ConversationSessionFixture.createId();
		assertThatThrownBy(
			() -> ConversationTurn.withId("id", sessionId, null, "response", Instant.now()))
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("query cannot be null or blank");
	}
}
