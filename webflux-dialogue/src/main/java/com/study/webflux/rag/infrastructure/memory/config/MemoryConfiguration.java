package com.study.webflux.rag.infrastructure.memory.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.ReactiveRedisConnectionFactory;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.data.redis.serializer.GenericToStringSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;
import org.springframework.data.redis.serializer.StringRedisSerializer;

import com.study.webflux.rag.infrastructure.dialogue.config.properties.RagDialogueProperties;
import com.study.webflux.rag.infrastructure.memory.adapter.MemoryExtractionConfig;

/** 기억 추출 및 조회 설정을 제공합니다. */
@Configuration
public class MemoryConfiguration {

	/** 기억 추출 설정을 생성합니다. */
	@Bean
	public MemoryExtractionConfig memoryExtractionConfig(RagDialogueProperties properties) {
		var memory = properties.getMemory();
		return new MemoryExtractionConfig(memory.getExtractionModel(),
			memory.getConversationThreshold(), memory.getImportanceBoost(),
			memory.getImportanceThreshold());
	}

	/** 대화 수 기준을 빈으로 노출합니다. */
	@Bean
	public int conversationThreshold(RagDialogueProperties properties) {
		return properties.getMemory().getConversationThreshold();
	}

	/** Long 값을 위한 ReactiveRedisTemplate을 생성합니다. */
	@Bean
	public ReactiveRedisTemplate<String, Long> reactiveRedisLongTemplate(
		ReactiveRedisConnectionFactory connectionFactory) {
		RedisSerializationContext<String, Long> context = RedisSerializationContext
			.<String, Long>newSerializationContext(new StringRedisSerializer())
			.value(new GenericToStringSerializer<>(Long.class)).build();

		return new ReactiveRedisTemplate<>(connectionFactory, context);
	}
}
