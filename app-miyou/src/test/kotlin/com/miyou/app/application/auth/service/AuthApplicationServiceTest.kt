package com.miyou.app.application.auth.service

import com.miyou.app.domain.auth.exception.InvalidRefreshTokenException
import com.miyou.app.domain.auth.model.AccessTokenIssued
import com.miyou.app.domain.auth.model.OAuthAccount
import com.miyou.app.domain.auth.model.OAuthLoginResult
import com.miyou.app.domain.auth.model.Provider
import com.miyou.app.domain.auth.model.RefreshToken
import com.miyou.app.domain.auth.port.JwtIssuer
import com.miyou.app.domain.auth.port.OAuthAccountRepository
import com.miyou.app.domain.auth.port.RefreshTokenRepository
import com.miyou.app.support.anyValue
import com.miyou.app.support.eqValue
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mock
import org.mockito.Mockito.never
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
import org.mockito.invocation.InvocationOnMock
import org.mockito.junit.jupiter.MockitoExtension
import org.springframework.dao.DuplicateKeyException
import reactor.core.publisher.Mono
import reactor.test.StepVerifier
import java.time.Instant

@ExtendWith(MockitoExtension::class)
@DisplayName("AuthApplicationService")
class AuthApplicationServiceTest {
    @Mock
    private lateinit var oAuthAccountRepository: OAuthAccountRepository

    @Mock
    private lateinit var refreshTokenRepository: RefreshTokenRepository

    @Mock
    private lateinit var jwtIssuer: JwtIssuer

    private lateinit var service: AuthApplicationService

    @BeforeEach
    fun setUp() {
        service = AuthApplicationService(oAuthAccountRepository, refreshTokenRepository, jwtIssuer)
    }

    @Test
    @DisplayName("loginOrRegister reuses the existing UserId for a known (provider, providerUserId)")
    fun loginOrRegister_existingAccount_reusesUserId() {
        val loginResult = OAuthLoginResult(Provider.GOOGLE, "google-sub-1", "a@b.com", "홍길동")
        val existingUserId = "existing-user-1"
        val existingAccount =
            OAuthAccount(
                "acc-1",
                Provider.GOOGLE,
                "google-sub-1",
                existingUserId,
                "a@b.com",
                "홍길동",
                Instant.now(),
            )

        `when`(oAuthAccountRepository.findByProviderAndProviderUserId(Provider.GOOGLE, "google-sub-1"))
            .thenReturn(Mono.just(existingAccount))
        `when`(jwtIssuer.issueAccessToken(eqValue(existingUserId)))
            .thenReturn(AccessTokenIssued("access-token", Instant.now().plusSeconds(900)))
        `when`(refreshTokenRepository.save(anyValue()))
            .thenAnswer { invocation: InvocationOnMock -> Mono.just(invocation.getArgument<RefreshToken>(0)) }

        StepVerifier
            .create(service.loginOrRegister(loginResult))
            .assertNext { tokens -> assertThat(tokens.accessToken).isEqualTo("access-token") }
            .verifyComplete()

        verify(oAuthAccountRepository, never()).save(anyValue())
    }

    @Test
    @DisplayName("loginOrRegister creates a new UserId when the account is unknown")
    fun loginOrRegister_newAccount_generatesNewUserId() {
        val loginResult = OAuthLoginResult(Provider.KAKAO, "kakao-1", null, "카카오유저")
        var savedAccount: OAuthAccount? = null

        `when`(oAuthAccountRepository.findByProviderAndProviderUserId(Provider.KAKAO, "kakao-1"))
            .thenReturn(Mono.empty())
        `when`(oAuthAccountRepository.save(anyValue()))
            .thenAnswer { invocation: InvocationOnMock ->
                val account = invocation.getArgument<OAuthAccount>(0)
                savedAccount = account
                Mono.just(account)
            }
        `when`(jwtIssuer.issueAccessToken(anyValue()))
            .thenReturn(AccessTokenIssued("access-token", Instant.now().plusSeconds(900)))
        `when`(refreshTokenRepository.save(anyValue()))
            .thenAnswer { invocation: InvocationOnMock -> Mono.just(invocation.getArgument<RefreshToken>(0)) }

        StepVerifier
            .create(service.loginOrRegister(loginResult))
            .assertNext { tokens -> assertThat(tokens.accessToken).isEqualTo("access-token") }
            .verifyComplete()

        assertThat(savedAccount).isNotNull
        assertThat(savedAccount!!.provider).isEqualTo(Provider.KAKAO)
        assertThat(savedAccount!!.providerUserId).isEqualTo("kakao-1")
    }

    @Test
    @DisplayName("loginOrRegister recovers from a concurrent-registration race (DuplicateKeyException)")
    fun loginOrRegister_duplicateKeyRace_reQueriesWinningAccount() {
        val loginResult = OAuthLoginResult(Provider.NAVER, "naver-1", null, null)
        val winningUserId = "race-winner-user"
        val winningAccount =
            OAuthAccount("acc-2", Provider.NAVER, "naver-1", winningUserId, null, null, Instant.now())

        `when`(oAuthAccountRepository.findByProviderAndProviderUserId(Provider.NAVER, "naver-1"))
            .thenReturn(Mono.empty(), Mono.just(winningAccount))
        `when`(oAuthAccountRepository.save(anyValue()))
            .thenReturn(Mono.error(DuplicateKeyException("duplicate")))
        `when`(jwtIssuer.issueAccessToken(eqValue(winningUserId)))
            .thenReturn(AccessTokenIssued("access-token", Instant.now().plusSeconds(900)))
        `when`(refreshTokenRepository.save(anyValue()))
            .thenAnswer { invocation: InvocationOnMock -> Mono.just(invocation.getArgument<RefreshToken>(0)) }

        StepVerifier
            .create(service.loginOrRegister(loginResult))
            .assertNext { tokens -> assertThat(tokens.accessToken).isEqualTo("access-token") }
            .verifyComplete()
    }

    @Test
    @DisplayName("refresh rotates the token: deletes the old one and issues a new pair")
    fun refresh_rotatesToken() {
        val userId = "refresh-user-1"
        val oldToken = RefreshToken("old-token-id", userId, Instant.now())

        `when`(refreshTokenRepository.findByTokenId("old-token-id")).thenReturn(Mono.just(oldToken))
        `when`(refreshTokenRepository.deleteByTokenId("old-token-id")).thenReturn(Mono.empty())
        `when`(jwtIssuer.issueAccessToken(eqValue(userId)))
            .thenReturn(AccessTokenIssued("new-access-token", Instant.now().plusSeconds(900)))
        `when`(refreshTokenRepository.save(anyValue()))
            .thenAnswer { invocation: InvocationOnMock -> Mono.just(invocation.getArgument<RefreshToken>(0)) }

        StepVerifier
            .create(service.refresh("old-token-id"))
            .assertNext { tokens -> assertThat(tokens.accessToken).isEqualTo("new-access-token") }
            .verifyComplete()

        verify(refreshTokenRepository).deleteByTokenId("old-token-id")
    }

    @Test
    @DisplayName("refresh fails with InvalidRefreshTokenException when the token is unknown")
    fun refresh_unknownToken_fails() {
        `when`(refreshTokenRepository.findByTokenId("missing-token")).thenReturn(Mono.empty())

        StepVerifier
            .create(service.refresh("missing-token"))
            .expectError(InvalidRefreshTokenException::class.java)
            .verify()

        verify(refreshTokenRepository, never()).deleteByTokenId(anyValue())
    }

    @Test
    @DisplayName("logout deletes the refresh token")
    fun logout_deletesRefreshToken() {
        `when`(refreshTokenRepository.deleteByTokenId("token-1")).thenReturn(Mono.empty())

        StepVerifier.create(service.logout("token-1")).verifyComplete()

        verify(refreshTokenRepository).deleteByTokenId("token-1")
    }
}
