package com.miyou.app.application.auth.service

import com.miyou.app.RagApplication
import com.miyou.app.domain.auth.exception.InvalidRefreshTokenException
import com.miyou.app.domain.auth.model.OAuthLoginResult
import com.miyou.app.domain.auth.model.Provider
import com.miyou.app.infrastructure.auth.repository.OAuthAccountMongoRepository
import com.miyou.app.support.ContainerizedIntegrationTestSupport
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles
import reactor.test.StepVerifier

/**
 * 실제 provider 리다이렉트는 자동화할 수 없으므로(라이브 Google/Kakao/Naver 앱 필요),
 * 조작된 OAuthLoginResult로 AuthApplicationService를 직접 호출해 로그인→토큰발급→
 * refresh→로그아웃 흐름을 실제 MongoDB/Redis까지 검증한다.
 * (DialoguePipelineCreditIntegrationTest와 동일하게, "실제 영속성, application 레이어
 * 아래로는 mock 없음" 스타일)
 */
@SpringBootTest(classes = [RagApplication::class])
@ActiveProfiles("test")
@DisplayName("[통합] OAuth 로그인 → 토큰 발급 → refresh → 로그아웃 전체 흐름")
class AuthFlowIntegrationTest : ContainerizedIntegrationTestSupport() {
    @Autowired
    private lateinit var authService: AuthApplicationService

    @Autowired
    private lateinit var oAuthAccountRepo: OAuthAccountMongoRepository

    @BeforeEach
    @AfterEach
    fun cleanUp() {
        oAuthAccountRepo.deleteAll().block()
    }

    @Test
    @DisplayName("첫 로그인 시 계정이 생성되고, 재로그인 시 같은 userId로 토큰이 재발급된다")
    fun loginOrRegister_firstThenRepeat_reusesSameUserId() {
        val loginResult = OAuthLoginResult(Provider.GOOGLE, "flow-test-google-sub", "flow@test.com", "플로우테스트")

        val firstTokens = authService.loginOrRegister(loginResult).block()!!
        assertThat(firstTokens.accessToken).isNotBlank()
        assertThat(firstTokens.refreshToken).isNotBlank()

        val secondTokens = authService.loginOrRegister(loginResult).block()!!
        assertThat(secondTokens.accessToken).isNotBlank()
        // 두 로그인 모두 같은 계정을 가리키므로 DB에는 계정이 하나만 있어야 한다
        StepVerifier
            .create(oAuthAccountRepo.findByProviderAndProviderUserId("GOOGLE", "flow-test-google-sub"))
            .assertNext { account -> assertThat(account.email).isEqualTo("flow@test.com") }
            .verifyComplete()
    }

    @Test
    @DisplayName("refresh는 기존 토큰을 폐기하고 새 토큰 쌍을 발급한다 (rotate-on-use)")
    fun refresh_rotatesOldTokenForNewPair() {
        val loginResult = OAuthLoginResult(Provider.KAKAO, "flow-test-kakao-sub", null, "카카오플로우")
        val initialTokens = authService.loginOrRegister(loginResult).block()!!

        val refreshedTokens = authService.refresh(initialTokens.refreshToken).block()!!
        // 액세스 토큰의 iat/exp는 초 단위라 같은 초 안에 재발급되면 바이트까지 동일할 수
        // 있음(이건 정상 — 회전이 실제로 보장해야 하는 건 refresh token의 유일성과
        // 옛 토큰의 폐기이지, 액세스 토큰의 매 순간 고유성이 아니다).
        assertThat(refreshedTokens.refreshToken).isNotEqualTo(initialTokens.refreshToken)

        // 회전된(폐기된) 옛 refresh token은 더 이상 쓸 수 없다
        StepVerifier
            .create(authService.refresh(initialTokens.refreshToken))
            .expectError(InvalidRefreshTokenException::class.java)
            .verify()
    }

    @Test
    @DisplayName("logout 후에는 해당 refresh token으로 재발급할 수 없다")
    fun logout_thenRefresh_fails() {
        val loginResult = OAuthLoginResult(Provider.NAVER, "flow-test-naver-sub", "flow-naver@test.com", null)
        val tokens = authService.loginOrRegister(loginResult).block()!!

        StepVerifier.create(authService.logout(tokens.refreshToken)).verifyComplete()

        StepVerifier
            .create(authService.refresh(tokens.refreshToken))
            .expectError(InvalidRefreshTokenException::class.java)
            .verify()
    }
}
