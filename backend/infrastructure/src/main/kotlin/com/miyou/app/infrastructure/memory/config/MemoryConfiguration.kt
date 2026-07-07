package com.miyou.app.infrastructure.memory.config

import com.miyou.app.infrastructure.dialogue.config.properties.RagDialogueProperties
import com.miyou.app.infrastructure.memory.adapter.MemoryExtractionConfig
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.data.redis.connection.ReactiveRedisConnectionFactory
import org.springframework.data.redis.core.ReactiveRedisTemplate
import org.springframework.data.redis.serializer.GenericToStringSerializer
import org.springframework.data.redis.serializer.RedisSerializationContext
import org.springframework.data.redis.serializer.StringRedisSerializer

/**
 * Configuration for memory adapters and Redis templates.
 */
@Configuration
class MemoryConfiguration {
    /** Create memory extraction settings bean. */
    @Bean
    fun memoryExtractionConfig(properties: RagDialogueProperties): MemoryExtractionConfig {
        val memory = properties.memory
        return MemoryExtractionConfig(
            memory.extractionModel,
            memory.conversationThreshold,
            memory.importanceBoost,
            memory.importanceThreshold,
        )
    }

    /** Exposes conversation threshold as bean. */
    @Bean
    fun conversationThreshold(properties: RagDialogueProperties): Int = properties.memory.conversationThreshold

    /** ReactiveRedisTemplate for Long values. */
    @Bean
    fun reactiveRedisLongTemplate(
        connectionFactory: ReactiveRedisConnectionFactory,
    ): ReactiveRedisTemplate<String, Long> {
        val context =
            RedisSerializationContext
                .newSerializationContext<String, Long>(
                    StringRedisSerializer(),
                ).value(GenericToStringSerializer(Long::class.java))
                .build()

        return ReactiveRedisTemplate(connectionFactory, context)
    }

    /** ReactiveRedisTemplate for String values. */
    @Bean("reactiveRedisStringTemplate")
    fun reactiveRedisStringTemplate(
        connectionFactory: ReactiveRedisConnectionFactory,
    ): ReactiveRedisTemplate<String, String> {
        val context =
            RedisSerializationContext
                .newSerializationContext<String, String>(
                    StringRedisSerializer(),
                ).value(StringRedisSerializer())
                .build()

        return ReactiveRedisTemplate(connectionFactory, context)
    }
}
