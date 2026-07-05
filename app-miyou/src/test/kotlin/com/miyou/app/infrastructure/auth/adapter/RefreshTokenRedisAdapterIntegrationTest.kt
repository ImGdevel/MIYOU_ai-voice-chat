package com.miyou.app.infrastructure.auth.adapter

import com.miyou.app.domain.auth.model.RefreshToken
import com.miyou.app.domain.dialogue.model.UserId
import com.miyou.app.infrastructure.auth.config.JwtProperties
import com.miyou.app.support.ContainerizedIntegrationTestSupport
import com.miyou.app.support.ReactiveRedisStringTemplateTestConfig
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.boot.autoconfigure.jackson.JacksonAutoConfiguration
import org.springframework.boot.test.autoconfigure.data.redis.DataRedisTest
import org.springframework.context.annotation.Import
import org.springframework.data.redis.core.ReactiveRedisTemplate
import org.springframework.test.context.ActiveProfiles
import reactor.test.StepVerifier
import java.time.Duration
import java.util.UUID

// @DataRedisTest는 Jackson 자동설정을 포함하지 않아 RefreshTokenRedisAdapter가 필요로
// 하는 ObjectMapper 빈이 없다 — JacksonAutoConfiguration을 명시적으로 끌어온다.
@DataRedisTest
@ActiveProfiles("test")
@Import(
    RefreshTokenRedisAdapter::class,
    ReactiveRedisStringTemplateTestConfig::class,
    JwtProperties::class,
    JacksonAutoConfiguration::class,
)
@DisplayName("[통합] RefreshToken Redis Adapter")
class RefreshTokenRedisAdapterIntegrationTest : ContainerizedIntegrationTestSupport() {
    @Autowired
    private lateinit var adapter: RefreshTokenRedisAdapter

    @Autowired
    @Qualifier("reactiveRedisStringTemplate")
    private lateinit var redisTemplate: ReactiveRedisTemplate<String, String>

    @Autowired
    private lateinit var jwtProperties: JwtProperties

    @Test
    @DisplayName("저장 후 tokenId로 조회하면 같은 userId를 되돌려준다")
    fun save_thenFindByTokenId_returnsSameUserId() {
        val userId = UserId.of("redis-integration-user-1")
        val token = RefreshToken.issue(userId)

        StepVerifier
            .create(adapter.save(token).then(adapter.findByTokenId(token.tokenId)))
            .assertNext { found -> assertThat(found.userId).isEqualTo(userId) }
            .verifyComplete()
    }

    @Test
    @DisplayName("저장 시 설정된 refresh-token-ttl-days만큼 실제 TTL이 설정된다")
    fun save_setsRealRedisTtl() {
        val token = RefreshToken.issue(UserId.of("redis-ttl-user"))

        StepVerifier
            .create(adapter.save(token).then(redisTemplate.getExpire("auth:refresh:" + token.tokenId)))
            .assertNext { ttl ->
                assertThat(ttl).isPositive()
                assertThat(ttl).isLessThanOrEqualTo(Duration.ofDays(jwtProperties.refreshTokenTtlDays))
            }.verifyComplete()
    }

    @Test
    @DisplayName("삭제 후에는 더 이상 조회되지 않는다")
    fun deleteByTokenId_thenFindByTokenId_returnsEmpty() {
        val token = RefreshToken.issue(UserId.of("redis-delete-user"))

        StepVerifier
            .create(
                adapter
                    .save(token)
                    .then(adapter.deleteByTokenId(token.tokenId))
                    .then(adapter.findByTokenId(token.tokenId)),
            ).verifyComplete()
    }

    @Test
    @DisplayName("존재하지 않는 tokenId 조회는 빈 결과를 반환한다")
    fun findByTokenId_notFound_returnsEmpty() {
        StepVerifier.create(adapter.findByTokenId(UUID.randomUUID().toString())).verifyComplete()
    }
}
