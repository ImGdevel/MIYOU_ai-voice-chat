package com.miyou.app.support

import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.data.redis.connection.ReactiveRedisConnectionFactory
import org.springframework.data.redis.core.ReactiveRedisTemplate
import org.springframework.data.redis.serializer.RedisSerializationContext
import org.springframework.data.redis.serializer.StringRedisSerializer

/**
 * 실제 MemoryConfiguration.reactiveRedisStringTemplate()과 동일한 빈만 필요한 좁은
 * 테스트 슬라이스용 — MemoryConfiguration 전체를 @Import하면 RagDialogueProperties가
 * 필요한 다른 @Bean 메서드(memoryExtractionConfig 등)까지 끌려와 깨진다.
 */
@TestConfiguration
class ReactiveRedisStringTemplateTestConfig {
    @Bean("reactiveRedisStringTemplate")
    fun reactiveRedisStringTemplate(
        connectionFactory: ReactiveRedisConnectionFactory,
    ): ReactiveRedisTemplate<String, String> {
        val context =
            RedisSerializationContext
                .newSerializationContext<String, String>(StringRedisSerializer())
                .value(StringRedisSerializer())
                .build()
        return ReactiveRedisTemplate(connectionFactory, context)
    }
}
