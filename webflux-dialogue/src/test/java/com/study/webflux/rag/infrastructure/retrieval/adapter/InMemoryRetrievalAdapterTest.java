package com.study.webflux.rag.infrastructure.retrieval.adapter;

import java.time.Instant;

import com.study.webflux.rag.domain.dialogue.model.ConversationSessionId;
import com.study.webflux.rag.domain.dialogue.model.ConversationTurn;
import com.study.webflux.rag.domain.dialogue.port.ConversationRepository;
import com.study.webflux.rag.fixture.ConversationSessionFixture;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Flux;
import reactor.test.StepVerifier;

import static org.assertj.core.api.AssertionsForInterfaceTypes.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class InMemoryRetrievalAdapterTest {

	@Mock
	private ConversationRepository conversationRepository;

	private InMemoryRetrievalAdapter adapter;

	@BeforeEach
	void setUp() {
		adapter = new InMemoryRetrievalAdapter(conversationRepository);
	}

	@Test
	@DisplayName("키워드 매칭으로 관련 문서 검색")
	void retrieve_keywordMatch_success() {
		ConversationSessionId sessionId = ConversationSessionFixture.createId();
		ConversationTurn turn1 = ConversationTurn.withId("id-1",
			sessionId,
			"자바 스프링 공부",
			"좋아요",
			Instant.now());
		ConversationTurn turn2 = ConversationTurn.withId("id-2",
			sessionId,
			"파이썬 배우기",
			"알겠습니다",
			Instant.now());
		ConversationTurn turn3 = ConversationTurn.withId("id-3",
			sessionId,
			"스프링 부트 설정",
			"네",
			Instant.now());

		when(conversationRepository.findAll(sessionId)).thenReturn(Flux.just(turn1, turn2, turn3));

		StepVerifier.create(adapter.retrieve(sessionId, "스프링 웹플럭스", 5))
			.assertNext(context -> {
				assertThat(context.query()).isEqualTo("스프링 웹플럭스");
				assertThat(context.documents()).hasSize(2);
				assertThat(context.documents().get(0).content()).isEqualTo("자바 스프링 공부");
				assertThat(context.documents().get(1).content()).isEqualTo("스프링 부트 설정");
			}).verifyComplete();
	}

	@Test
	@DisplayName("유사도 점수 기준 내림차순 정렬")
	void retrieve_sortedByScore() {
		ConversationSessionId sessionId = ConversationSessionFixture.createId();
		ConversationTurn turn1 = ConversationTurn.create(sessionId, "자바");
		ConversationTurn turn2 = ConversationTurn.create(sessionId, "자바 스프링");
		ConversationTurn turn3 = ConversationTurn.create(sessionId, "자바 스프링 부트");

		when(conversationRepository.findAll(sessionId)).thenReturn(Flux.just(turn1, turn2, turn3));

		StepVerifier.create(adapter.retrieve(sessionId, "자바 스프링", 5)).assertNext(context -> {
			assertThat(context.documents()).hasSize(3);
			assertThat(context.documents().get(0).content()).isEqualTo("자바 스프링");
			assertThat(context.documents().get(0).score().value()).isEqualTo(2);
		}).verifyComplete();
	}

	@Test
	@DisplayName("topK 제한으로 상위 결과만 반환")
	void retrieve_topK_limit() {
		ConversationSessionId sessionId = ConversationSessionFixture.createId();
		ConversationTurn turn1 = ConversationTurn.create(sessionId, "자바 프로그래밍");
		ConversationTurn turn2 = ConversationTurn.create(sessionId, "자바 스프링");
		ConversationTurn turn3 = ConversationTurn.create(sessionId, "자바 개발");
		ConversationTurn turn4 = ConversationTurn.create(sessionId, "자바 튜토리얼");

		when(conversationRepository.findAll(sessionId))
			.thenReturn(Flux.just(turn1, turn2, turn3, turn4));

		StepVerifier.create(adapter.retrieve(sessionId, "자바", 2)).assertNext(context -> {
			assertThat(context.documents()).hasSize(2);
		}).verifyComplete();
	}

	@Test
	@DisplayName("관련 없는 문서는 필터링")
	void retrieve_irrelevant_filtered() {
		ConversationSessionId sessionId = ConversationSessionFixture.createId();
		ConversationTurn turn1 = ConversationTurn.create(sessionId, "자바 스프링");
		ConversationTurn turn2 = ConversationTurn.create(sessionId, "파이썬 장고");
		ConversationTurn turn3 = ConversationTurn.create(sessionId, "자바스크립트 리액트");

		when(conversationRepository.findAll(sessionId)).thenReturn(Flux.just(turn1, turn2, turn3));

		StepVerifier.create(adapter.retrieve(sessionId, "자바 스프링", 10))
			.assertNext(context -> {
				assertThat(context.documents()).hasSize(1);
				assertThat(context.documents().get(0).content()).isEqualTo("자바 스프링");
			}).verifyComplete();
	}

	@Test
	@DisplayName("빈 결과 반환")
	void retrieve_empty_conversations() {
		ConversationSessionId sessionId = ConversationSessionFixture.createId();
		when(conversationRepository.findAll(sessionId)).thenReturn(Flux.empty());

		StepVerifier.create(adapter.retrieve(sessionId, "테스트", 5)).assertNext(context -> {
			assertThat(context.isEmpty()).isTrue();
			assertThat(context.documents()).isEmpty();
		}).verifyComplete();
	}

	@Test
	@DisplayName("모든 문서가 관련 없으면 빈 결과")
	void retrieve_noRelevant_emptyResult() {
		ConversationSessionId sessionId = ConversationSessionFixture.createId();
		ConversationTurn turn1 = ConversationTurn.create(sessionId, "파이썬 장고");
		ConversationTurn turn2 = ConversationTurn.create(sessionId, "루비 레일즈");

		when(conversationRepository.findAll(sessionId)).thenReturn(Flux.just(turn1, turn2));

		StepVerifier.create(adapter.retrieve(sessionId, "자바 스프링", 10))
			.assertNext(context -> {
				assertThat(context.isEmpty()).isTrue();
			}).verifyComplete();
	}

	@Test
	@DisplayName("대소문자 구분 없이 매칭")
	void retrieve_caseInsensitive() {
		ConversationSessionId sessionId = ConversationSessionFixture.createId();
		ConversationTurn turn = ConversationTurn.create(sessionId, "JAVA Spring");

		when(conversationRepository.findAll(sessionId)).thenReturn(Flux.just(turn));

		StepVerifier.create(adapter.retrieve(sessionId, "java spring", 5))
			.assertNext(context -> {
				assertThat(context.documents()).hasSize(1);
				assertThat(context.documents().get(0).score().value()).isEqualTo(2);
			}).verifyComplete();
	}

	@Test
	@DisplayName("공백 문자로 토큰화")
	void retrieve_tokenization() {
		ConversationSessionId sessionId = ConversationSessionFixture.createId();
		ConversationTurn turn = ConversationTurn.create(sessionId, "자바   스프링    부트");

		when(conversationRepository.findAll(sessionId)).thenReturn(Flux.just(turn));

		StepVerifier.create(adapter.retrieve(sessionId, "스프링 자바", 5))
			.assertNext(context -> {
				assertThat(context.documents()).hasSize(1);
				assertThat(context.documents().get(0).score().value()).isEqualTo(2);
			}).verifyComplete();
	}

	@Test
	@DisplayName("메모리 검색은 빈 결과 반환")
	void retrieveMemories_returnsEmpty() {
		ConversationSessionId sessionId = ConversationSessionFixture.createId();
		StepVerifier.create(adapter.retrieveMemories(sessionId, "테스트", 5))
			.assertNext(result -> {
				assertThat(result.isEmpty()).isTrue();
				assertThat(result.allMemories()).isEmpty();
			}).verifyComplete();
	}

	@Test
	@DisplayName("유효한 쿼리만 검색 대상에 포함")
	void retrieve_validQueriesOnly() {
		ConversationSessionId sessionId = ConversationSessionFixture.createId();
		ConversationTurn turn1 = ConversationTurn.create(sessionId, "관련 없는 내용");
		ConversationTurn turn2 = ConversationTurn.create(sessionId, "완전히 다른 주제");

		when(conversationRepository.findAll(sessionId)).thenReturn(Flux.just(turn1, turn2));

		StepVerifier.create(adapter.retrieve(sessionId, "테스트", 5)).assertNext(context -> {
			assertThat(context.documents()).isEmpty();
		}).verifyComplete();
	}

	@Test
	@DisplayName("교집합 크기로 유사도 계산")
	void retrieve_intersectionSize() {
		ConversationSessionId sessionId = ConversationSessionFixture.createId();
		ConversationTurn turn1 = ConversationTurn.create(sessionId, "a b c d");
		ConversationTurn turn2 = ConversationTurn.create(sessionId, "a b e f");
		ConversationTurn turn3 = ConversationTurn.create(sessionId, "a e f g");

		when(conversationRepository.findAll(sessionId)).thenReturn(Flux.just(turn1, turn2, turn3));

		StepVerifier.create(adapter.retrieve(sessionId, "a b", 5)).assertNext(context -> {
			assertThat(context.documents()).hasSize(3);
			assertThat(context.documents().get(0).score().value()).isEqualTo(2);
			assertThat(context.documents().get(1).score().value()).isEqualTo(2);
			assertThat(context.documents().get(2).score().value()).isEqualTo(1);
		}).verifyComplete();
	}

	@Test
	@DisplayName("저장소 에러 시 에러 전파")
	void retrieve_error_propagates() {
		ConversationSessionId sessionId = ConversationSessionFixture.createId();
		when(conversationRepository.findAll(sessionId)).thenReturn(
			Flux.error(new RuntimeException("DB error")));

		StepVerifier.create(adapter.retrieve(sessionId, "테스트", 5))
			.expectErrorMatches(throwable -> throwable.getMessage().contains("DB error"))
			.verify();
	}
}
