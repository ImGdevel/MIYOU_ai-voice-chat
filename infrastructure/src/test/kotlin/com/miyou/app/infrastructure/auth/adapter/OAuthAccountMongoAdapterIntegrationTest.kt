package com.miyou.app.infrastructure.auth.adapter

import com.miyou.app.config.annotation.ReactiveRepositoryTest
import com.miyou.app.domain.auth.model.OAuthAccount
import com.miyou.app.domain.auth.model.Provider
import com.miyou.app.infrastructure.auth.repository.OAuthAccountMongoRepository
import com.miyou.app.support.ContainerizedIntegrationTestSupport
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.annotation.Import
import org.springframework.dao.DuplicateKeyException
import reactor.test.StepVerifier
import java.time.Instant
import java.util.UUID

@ReactiveRepositoryTest
@Import(OAuthAccountMongoAdapter::class)
@DisplayName("[통합] OAuthAccount MongoDB Adapter")
class OAuthAccountMongoAdapterIntegrationTest : ContainerizedIntegrationTestSupport() {
    @Autowired
    private lateinit var mongoRepository: OAuthAccountMongoRepository

    @Autowired
    private lateinit var adapter: OAuthAccountMongoAdapter

    @BeforeEach
    @AfterEach
    fun cleanUp() {
        mongoRepository.deleteAll().block()
    }

    @Test
    @DisplayName("저장 후 (provider, providerUserId)로 조회할 수 있다")
    fun save_thenFindByProviderAndProviderUserId_returnsAccount() {
        val userId = "integration-oauth-user-1"
        val account =
            OAuthAccount(
                UUID.randomUUID().toString(),
                Provider.GOOGLE,
                "google-sub-1",
                userId,
                "a@b.com",
                "홍길동",
                Instant.now()
            )

        StepVerifier
            .create(
                adapter
                    .save(account)
                    .then(adapter.findByProviderAndProviderUserId(Provider.GOOGLE, "google-sub-1")),
            ).assertNext { found ->
                assertThat(found.userId).isEqualTo(userId)
                assertThat(found.email).isEqualTo("a@b.com")
            }.verifyComplete()
    }

    @Test
    @DisplayName("(provider, providerUserId) 복합 유니크 인덱스가 중복 저장을 거부한다")
    fun compoundUniqueIndex_rejectsDuplicateProviderAndProviderUserId() {
        val account1 =
            OAuthAccount(
                UUID.randomUUID().toString(),
                Provider.KAKAO,
                "kakao-dup-1",
                "user-a",
                null,
                null,
                Instant.now()
            )
        val account2 =
            OAuthAccount(
                UUID.randomUUID().toString(),
                Provider.KAKAO,
                "kakao-dup-1",
                "user-b",
                null,
                null,
                Instant.now()
            )

        StepVerifier
            .create(adapter.save(account1).then(adapter.save(account2)))
            .expectError(DuplicateKeyException::class.java)
            .verify()
    }

    @Test
    @DisplayName("존재하지 않는 (provider, providerUserId) 조회는 빈 결과를 반환한다")
    fun findByProviderAndProviderUserId_notFound_returnsEmpty() {
        StepVerifier
            .create(adapter.findByProviderAndProviderUserId(Provider.NAVER, "no-such-user"))
            .verifyComplete()
    }
}
