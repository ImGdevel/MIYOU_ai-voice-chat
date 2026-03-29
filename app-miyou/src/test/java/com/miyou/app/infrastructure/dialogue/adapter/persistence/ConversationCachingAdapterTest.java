package com.miyou.app.infrastructure.dialogue.adapter.persistence;

import java.time.Instant;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.redis.core.ReactiveListOperations;
import org.springframework.data.redis.core.ReactiveRedisTemplate;

import com.miyou.app.domain.dialogue.model.ConversationSessionId;
import com.miyou.app.domain.dialogue.model.ConversationTurn;
import com.miyou.app.fixture.ConversationSessionFixture;
import com.miyou.app.infrastructure.dialogue.adapter.persistence.document.ConversationDocument;
import com.miyou.app.infrastructure.dialogue.config.properties.RagDialogueProperties;
import com.miyou.app.infrastructure.dialogue.repository.ConversationMongoRepository;
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
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ConversationCachingAdapterTest {

	private static final String KEY_PREFIX = "dialogue:conversation:history:";
	private static final int MAX_HISTORY_SIZE = 10;
	private static final int TTL_HOURS = 24;

	@Mock
	private ConversationMongoRepository mongoRepository;

	@Mock
	private ReactiveRedisTemplate<String, String> redisTemplate;

	@Mock
	private ReactiveListOperations<String, String> listOps;

	private ConversationCachingAdapter adapter;

	@BeforeEach
	void setUp() {
		RagDialogueProperties properties = new RagDialogueProperties();
		RagDialogueProperties.Cache cache = new RagDialogueProperties.Cache();
		cache.setMaxHistorySize(MAX_HISTORY_SIZE);
		cache.setTtlHours(TTL_HOURS);
		properties.setCache(cache);

		adapter = new ConversationCachingAdapter(mongoRepository, redisTemplate, properties);
	}

	// ── save ─────────────────────────────────────────────────────────────────

	@Test
	@DisplayName("save: MongoDB 저장 후 Redis 캐시에 append")
	void save_appendsToCache() {
		ConversationSessionId sessionId = ConversationSessionFixture.createId();
		ConversationTurn turn = ConversationTurn.create(sessionId, "안녕");
		String cacheKey = KEY_PREFIX + sessionId.value();
		ConversationDocument saved = new ConversationDocument("id-1", sessionId.value(), "안녕", null,
			Instant.now());

		when(mongoRepository.save(any())).thenReturn(Mono.just(saved));
		when(redisTemplate.opsForList()).thenReturn(listOps);
		when(listOps.rightPush(eq(cacheKey), anyString())).thenReturn(Mono.just(1L));
		when(listOps.trim(eq(cacheKey), anyLong(), anyLong())).thenReturn(Mono.just(true));
		when(redisTemplate.expire(eq(cacheKey), any())).thenReturn(Mono.just(true));

		StepVerifier.create(adapter.save(turn))
			.assertNext(result -> {
				assertThat(result.id()).isEqualTo("id-1");
				assertThat(result.query()).isEqualTo("안녕");
			})
			.verifyComplete();

		verify(mongoRepository).save(any());
		verify(listOps).rightPush(eq(cacheKey), anyString());
	}

	@Test
	@DisplayName("save: Redis write 실패해도 MongoDB 저장 결과 정상 반환 (non-fatal)")
	void save_redisFails_stillReturnsResult() {
		ConversationSessionId sessionId = ConversationSessionFixture.createId();
		ConversationTurn turn = ConversationTurn.create(sessionId, "질문");
		ConversationDocument saved = new ConversationDocument("id-2", sessionId.value(), "질문", null,
			Instant.now());

		when(mongoRepository.save(any())).thenReturn(Mono.just(saved));
		when(redisTemplate.opsForList()).thenReturn(listOps);
		when(listOps.rightPush(anyString(), anyString()))
			.thenReturn(Mono.error(new RuntimeException("Redis down")));

		StepVerifier.create(adapter.save(turn))
			.assertNext(result -> assertThat(result.id()).isEqualTo("id-2"))
			.verifyComplete();
	}

	@Test
	@DisplayName("save: MongoDB 저장 실패 시 에러 전파")
	void save_mongoFails_propagatesError() {
		ConversationSessionId sessionId = ConversationSessionFixture.createId();
		ConversationTurn turn = ConversationTurn.create(sessionId, "질문");

		when(mongoRepository.save(any()))
			.thenReturn(Mono.error(new RuntimeException("Mongo down")));

		StepVerifier.create(adapter.save(turn))
			.expectErrorMatches(e -> e.getMessage().contains("Mongo down"))
			.verify();
	}

	// ── findRecent ───────────────────────────────────────────────────────────

	@Test
	@DisplayName("findRecent: Redis 캐시 hit 시 MongoDB 조회 없이 반환")
	void findRecent_cacheHit_returnsCached() {
		ConversationSessionId sessionId = ConversationSessionFixture.createId();
		String cacheKey = KEY_PREFIX + sessionId.value();
		Instant now = Instant.now();

		// serialize manually to match adapter's format
		String json = String.format(
			"{\"id\":\"id-1\",\"sessionId\":\"%s\",\"query\":\"질문\",\"response\":\"답변\",\"createdAt\":\"%s\"}",
			sessionId.value(),
			now.toString());

		when(redisTemplate.opsForList()).thenReturn(listOps);
		when(listOps.range(eq(cacheKey), anyLong(), anyLong())).thenReturn(Flux.just(json));

		StepVerifier.create(adapter.findRecent(sessionId, 5))
			.assertNext(result -> {
				assertThat(result.id()).isEqualTo("id-1");
				assertThat(result.query()).isEqualTo("질문");
				assertThat(result.response()).isEqualTo("답변");
			})
			.verifyComplete();

		verify(mongoRepository, never()).findBySessionIdOrderByCreatedAtAsc(anyString(), any());
	}

	@Test
	@DisplayName("findRecent: Redis 캐시 miss 시 MongoDB fallback 및 캐시 warmup")
	void findRecent_cacheMiss_fallsBackToMongo() {
		ConversationSessionId sessionId = ConversationSessionFixture.createId();
		String cacheKey = KEY_PREFIX + sessionId.value();
		Instant now = Instant.now();
		ConversationDocument doc = new ConversationDocument("id-1", sessionId.value(), "질문", "답변",
			now);

		when(redisTemplate.opsForList()).thenReturn(listOps);
		when(listOps.range(eq(cacheKey), anyLong(), anyLong())).thenReturn(Flux.empty());
		when(mongoRepository.findBySessionIdOrderByCreatedAtAsc(eq(sessionId.value()),
			any(PageRequest.class)))
			.thenReturn(Flux.just(doc));
		when(listOps.rightPushAll(eq(cacheKey), any(String[].class))).thenReturn(Mono.just(1L));
		when(listOps.trim(eq(cacheKey), anyLong(), anyLong())).thenReturn(Mono.just(true));
		when(redisTemplate.expire(eq(cacheKey), any())).thenReturn(Mono.just(true));

		StepVerifier.create(adapter.findRecent(sessionId, 5))
			.assertNext(result -> {
				assertThat(result.id()).isEqualTo("id-1");
				assertThat(result.query()).isEqualTo("질문");
			})
			.verifyComplete();

		verify(mongoRepository).findBySessionIdOrderByCreatedAtAsc(eq(sessionId.value()), any());
		verify(listOps).rightPushAll(eq(cacheKey), any(String[].class));
	}

	@Test
	@DisplayName("findRecent: Redis 오류 시 MongoDB fallback")
	void findRecent_redisError_fallsBackToMongo() {
		ConversationSessionId sessionId = ConversationSessionFixture.createId();
		String cacheKey = KEY_PREFIX + sessionId.value();
		Instant now = Instant.now();
		ConversationDocument doc = new ConversationDocument("id-2", sessionId.value(), "오류 후 복구",
			null, now);

		when(redisTemplate.opsForList()).thenReturn(listOps);
		when(listOps.range(eq(cacheKey), anyLong(), anyLong()))
			.thenReturn(Flux.error(new RuntimeException("Redis timeout")));
		when(mongoRepository.findBySessionIdOrderByCreatedAtAsc(eq(sessionId.value()),
			any(PageRequest.class)))
			.thenReturn(Flux.just(doc));
		when(listOps.rightPushAll(eq(cacheKey), any(String[].class))).thenReturn(Mono.just(1L));
		when(listOps.trim(eq(cacheKey), anyLong(), anyLong())).thenReturn(Mono.just(true));
		when(redisTemplate.expire(eq(cacheKey), any())).thenReturn(Mono.just(true));

		StepVerifier.create(adapter.findRecent(sessionId, 5))
			.assertNext(result -> assertThat(result.id()).isEqualTo("id-2"))
			.verifyComplete();
	}

	@Test
	@DisplayName("findRecent: MongoDB도 비어 있으면 빈 Flux 반환")
	void findRecent_bothEmpty_returnsEmpty() {
		ConversationSessionId sessionId = ConversationSessionFixture.createId();
		String cacheKey = KEY_PREFIX + sessionId.value();

		when(redisTemplate.opsForList()).thenReturn(listOps);
		when(listOps.range(eq(cacheKey), anyLong(), anyLong())).thenReturn(Flux.empty());
		when(mongoRepository.findBySessionIdOrderByCreatedAtAsc(eq(sessionId.value()),
			any(PageRequest.class)))
			.thenReturn(Flux.empty());

		StepVerifier.create(adapter.findRecent(sessionId, 5)).verifyComplete();
	}

	// ── evict ────────────────────────────────────────────────────────────────

	@Test
	@DisplayName("evict: 세션 캐시 키 삭제")
	void evict_deletesKey() {
		ConversationSessionId sessionId = ConversationSessionFixture.createId();
		String cacheKey = KEY_PREFIX + sessionId.value();

		when(redisTemplate.delete(cacheKey)).thenReturn(Mono.just(1L));

		StepVerifier.create(adapter.evict(sessionId)).verifyComplete();

		verify(redisTemplate).delete(cacheKey);
	}
}
