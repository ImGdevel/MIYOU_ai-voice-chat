package com.study.webflux.rag.infrastructure.dialogue.adapter.persistence;

import java.time.Instant;

import org.springframework.data.domain.PageRequest;

import com.study.webflux.rag.domain.dialogue.entity.ConversationEntity;
import com.study.webflux.rag.domain.dialogue.model.ConversationTurn;
import com.study.webflux.rag.domain.dialogue.model.UserId;
import com.study.webflux.rag.infrastructure.dialogue.repository.ConversationMongoRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ConversationMongoAdapterTest {

	@Mock
	private ConversationMongoRepository mongoRepository;

	private ConversationMongoAdapter adapter;

	@BeforeEach
	void setUp() {
		adapter = new ConversationMongoAdapter(mongoRepository);
	}

	@Test
	@DisplayName("대화 턴 저장 성공")
	void save_success() {
		Instant now = Instant.now();
		UserId userId = UserId.of("user-1");
		ConversationTurn turn = ConversationTurn.create(userId, "안녕하세요");
		ConversationEntity savedEntity = new ConversationEntity("id-123", "user-1", "안녕하세요",
			null, now);

		when(mongoRepository.save(any(ConversationEntity.class))).thenReturn(
			Mono.just(savedEntity));

		StepVerifier.create(adapter.save(turn)).assertNext(result -> {
			assertThat(result.id()).isEqualTo("id-123");
			assertThat(result.userId()).isEqualTo(userId);
			assertThat(result.query()).isEqualTo("안녕하세요");
			assertThat(result.createdAt()).isEqualTo(now);
		}).verifyComplete();

		verify(mongoRepository).save(any(ConversationEntity.class));
	}

	@Test
	@DisplayName("응답 포함 대화 턴 저장 성공")
	void save_withResponse_success() {
		Instant now = Instant.now();
		UserId userId = UserId.of("user-1");
		ConversationTurn turn = ConversationTurn.withId("id-456", userId, "질문", "답변", now);
		ConversationEntity savedEntity = new ConversationEntity("id-456", "user-1", "질문", "답변",
			now);

		when(mongoRepository.save(any(ConversationEntity.class))).thenReturn(
			Mono.just(savedEntity));

		StepVerifier.create(adapter.save(turn)).assertNext(result -> {
			assertThat(result.id()).isEqualTo("id-456");
			assertThat(result.userId()).isEqualTo(userId);
			assertThat(result.query()).isEqualTo("질문");
			assertThat(result.response()).isEqualTo("답변");
		}).verifyComplete();
	}

	@Test
	@DisplayName("최근 대화 조회 시 역순으로 정렬하여 반환")
	void findRecent_success() {
		UserId userId = UserId.of("user-1");
		Instant time1 = Instant.parse("2025-01-01T10:00:00Z");
		Instant time2 = Instant.parse("2025-01-01T11:00:00Z");
		Instant time3 = Instant.parse("2025-01-01T12:00:00Z");

		ConversationEntity entity1 = new ConversationEntity("id-1", "user-1", "첫 번째", "응답1",
			time1);
		ConversationEntity entity2 = new ConversationEntity("id-2", "user-1", "두 번째", "응답2",
			time2);
		ConversationEntity entity3 = new ConversationEntity("id-3", "user-1", "세 번째", "응답3",
			time3);

		when(mongoRepository.findByUserIdOrderByCreatedAtDesc("user-1", PageRequest.of(0, 10)))
			.thenReturn(Flux.just(entity3, entity2, entity1));

		StepVerifier.create(adapter.findRecent(userId, 10)).assertNext(result -> {
			assertThat(result.id()).isEqualTo("id-1");
			assertThat(result.userId()).isEqualTo(userId);
			assertThat(result.query()).isEqualTo("첫 번째");
		}).assertNext(result -> {
			assertThat(result.id()).isEqualTo("id-2");
			assertThat(result.userId()).isEqualTo(userId);
			assertThat(result.query()).isEqualTo("두 번째");
		}).assertNext(result -> {
			assertThat(result.id()).isEqualTo("id-3");
			assertThat(result.userId()).isEqualTo(userId);
			assertThat(result.query()).isEqualTo("세 번째");
		}).verifyComplete();
	}

	@Test
	@DisplayName("최근 대화 조회 시 결과가 없으면 빈 Flux 반환")
	void findRecent_empty() {
		UserId userId = UserId.of("user-1");
		when(mongoRepository.findByUserIdOrderByCreatedAtDesc("user-1", PageRequest.of(0, 10)))
			.thenReturn(Flux.empty());

		StepVerifier.create(adapter.findRecent(userId, 10)).verifyComplete();
	}

	@Test
	@DisplayName("단일 대화 턴 조회 시 순서 유지")
	void findRecent_singleTurn() {
		Instant now = Instant.now();
		UserId userId = UserId.of("user-1");
		ConversationEntity entity = new ConversationEntity("id-1", "user-1", "질문", "답변", now);

		when(mongoRepository.findByUserIdOrderByCreatedAtDesc("user-1", PageRequest.of(0, 10)))
			.thenReturn(Flux.just(entity));

		StepVerifier.create(adapter.findRecent(userId, 10)).assertNext(result -> {
			assertThat(result.id()).isEqualTo("id-1");
			assertThat(result.userId()).isEqualTo(userId);
			assertThat(result.query()).isEqualTo("질문");
			assertThat(result.response()).isEqualTo("답변");
		}).verifyComplete();
	}

	@Test
	@DisplayName("모든 대화 조회 성공")
	void findAll_success() {
		UserId userId = UserId.of("user-1");
		Instant time1 = Instant.now();
		Instant time2 = time1.plusSeconds(60);

		ConversationEntity entity1 = new ConversationEntity("id-1", "user-1", "첫 번째", "응답1",
			time1);
		ConversationEntity entity2 = new ConversationEntity("id-2", "user-1", "두 번째", "응답2",
			time2);

		when(mongoRepository.findAllByUserId("user-1")).thenReturn(Flux.just(entity1, entity2));

		StepVerifier.create(adapter.findAll(userId)).assertNext(result -> {
			assertThat(result.id()).isEqualTo("id-1");
			assertThat(result.userId()).isEqualTo(userId);
			assertThat(result.createdAt()).isEqualTo(time1);
		}).assertNext(result -> {
			assertThat(result.id()).isEqualTo("id-2");
			assertThat(result.userId()).isEqualTo(userId);
			assertThat(result.createdAt()).isEqualTo(time2);
		}).verifyComplete();
	}

	@Test
	@DisplayName("모든 대화 조회 시 결과가 없으면 빈 Flux 반환")
	void findAll_empty() {
		UserId userId = UserId.of("user-1");
		when(mongoRepository.findAllByUserId("user-1")).thenReturn(Flux.empty());

		StepVerifier.create(adapter.findAll(userId)).verifyComplete();
	}

	@Test
	@DisplayName("저장 실패 시 에러 전파")
	void save_error_propagates() {
		ConversationTurn turn = ConversationTurn.create(UserId.of("user-1"), "에러 테스트");

		when(mongoRepository.save(any(ConversationEntity.class))).thenReturn(
			Mono.error(new RuntimeException("MongoDB connection failed")));

		StepVerifier.create(adapter.save(turn))
			.expectErrorMatches(
				throwable -> throwable.getMessage().contains("MongoDB connection failed"))
			.verify();
	}

	@Test
	@DisplayName("조회 실패 시 에러 전파")
	void findRecent_error_propagates() {
		UserId userId = UserId.of("user-1");
		when(mongoRepository.findByUserIdOrderByCreatedAtDesc("user-1", PageRequest.of(0, 10)))
			.thenReturn(Flux.error(new RuntimeException("Query timeout")));

		StepVerifier.create(adapter.findRecent(userId, 10))
			.expectErrorMatches(throwable -> throwable.getMessage().contains("Query timeout"))
			.verify();
	}
}
