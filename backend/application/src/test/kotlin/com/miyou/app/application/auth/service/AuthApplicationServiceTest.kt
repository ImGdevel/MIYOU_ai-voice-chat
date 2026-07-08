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
@DisplayName("인증 애플리케이션 서비스")
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
    @DisplayName("기존에 등록된 계정(provider, providerUserId)의 경우 기존 UserId를 재사용한다")
    fun loginOrRegister_existingAccount_reusesUserId() {
        // given
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

        // 기존 계정이 이미 존재하는 상황 모킹
        `when`(oAuthAccountRepository.findByProviderAndProviderUserId(Provider.GOOGLE, "google-sub-1"))
            .thenReturn(Mono.just(existingAccount))
        `when`(jwtIssuer.issueAccessToken(eqValue(existingUserId)))
            .thenReturn(AccessTokenIssued("access-token", Instant.now().plusSeconds(900)))
        `when`(refreshTokenRepository.save(anyValue()))
            .thenAnswer { invocation: InvocationOnMock -> Mono.just(invocation.getArgument<RefreshToken>(0)) }

        // when & then
        StepVerifier
            .create(service.loginOrRegister(loginResult))
            .assertNext { tokens ->
                // 기존 사용자 ID로 엑세스 토큰이 발행되었는지 확인
                assertThat(tokens.accessToken).isEqualTo("access-token")
            }.verifyComplete()

        // 신규 가입이 아니므로 save는 호출되지 않아야 함
        verify(oAuthAccountRepository, never()).save(anyValue())
    }

    @Test
    @DisplayName("처음 보는 계정의 경우 새로운 UserId를 생성하고 가입시킨다")
    fun loginOrRegister_newAccount_generatesNewUserId() {
        // given
        val loginResult = OAuthLoginResult(Provider.KAKAO, "kakao-1", null, "카카오유저")
        var savedAccount: OAuthAccount? = null

        // 계정이 존재하지 않는 상황 모킹
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

        // when & then
        StepVerifier
            .create(service.loginOrRegister(loginResult))
            .assertNext { tokens ->
                // 새로 가입되어 엑세스 토큰이 발행되었는지 확인
                assertThat(tokens.accessToken).isEqualTo("access-token")
            }.verifyComplete()

        // 새로운 사용자가 데이터베이스에 저장되었는지 검증
        assertThat(savedAccount).isNotNull
        assertThat(savedAccount!!.provider).isEqualTo(Provider.KAKAO)
        assertThat(savedAccount!!.providerUserId).isEqualTo("kakao-1")
    }

    @Test
    @DisplayName("동시 가입 경쟁이 발생하더라도 DuplicateKeyException을 극복하고 가입된 계정을 재조회하여 로그인 성공시킨다")
    fun loginOrRegister_duplicateKeyRace_reQueriesWinningAccount() {
        // given
        val loginResult = OAuthLoginResult(Provider.NAVER, "naver-1", null, null)
        val winningUserId = "race-winner-user"
        val winningAccount =
            OAuthAccount("acc-2", Provider.NAVER, "naver-1", winningUserId, null, null, Instant.now())

        // 처음 조회할 때는 없었지만, 등록할 때 중복 예외(DuplicateKeyException)가 발생하는 상황 모킹
        `when`(oAuthAccountRepository.findByProviderAndProviderUserId(Provider.NAVER, "naver-1"))
            .thenReturn(Mono.empty(), Mono.just(winningAccount))
        `when`(oAuthAccountRepository.save(anyValue()))
            .thenReturn(Mono.error(DuplicateKeyException("duplicate")))
        `when`(jwtIssuer.issueAccessToken(eqValue(winningUserId)))
            .thenReturn(AccessTokenIssued("access-token", Instant.now().plusSeconds(900)))
        `when`(refreshTokenRepository.save(anyValue()))
            .thenAnswer { invocation: InvocationOnMock -> Mono.just(invocation.getArgument<RefreshToken>(0)) }

        // when & then: 예외 복구 로직이 실행되어 최종적으로 엑세스 토큰이 리턴되어야 함
        StepVerifier
            .create(service.loginOrRegister(loginResult))
            .assertNext { tokens -> assertThat(tokens.accessToken).isEqualTo("access-token") }
            .verifyComplete()
    }

    @Test
    @DisplayName("토큰 갱신 시 기존 리프레시 토큰을 삭제하고 새로운 토큰 쌍을 발급(로테이션)한다")
    fun refresh_rotatesToken() {
        // given
        val userId = "refresh-user-1"
        val oldToken = RefreshToken("old-token-id", userId, Instant.now())

        `when`(refreshTokenRepository.findByTokenId("old-token-id")).thenReturn(Mono.just(oldToken))
        `when`(refreshTokenRepository.deleteByTokenId("old-token-id")).thenReturn(Mono.empty())
        `when`(jwtIssuer.issueAccessToken(eqValue(userId)))
            .thenReturn(AccessTokenIssued("new-access-token", Instant.now().plusSeconds(900)))
        `when`(refreshTokenRepository.save(anyValue()))
            .thenAnswer { invocation: InvocationOnMock -> Mono.just(invocation.getArgument<RefreshToken>(0)) }

        // when & then: 토큰 로테이션이 수행되고 새 엑세스 토큰이 확인됨
        StepVerifier
            .create(service.refresh("old-token-id"))
            .assertNext { tokens -> assertThat(tokens.accessToken).isEqualTo("new-access-token") }
            .verifyComplete()

        // 기존 사용된 리프레시 토큰이 정상적으로 무효화(삭제) 되었는지 확인
        verify(refreshTokenRepository).deleteByTokenId("old-token-id")
    }

    @Test
    @DisplayName("존재하지 않는 리프레시 토큰으로 갱신 요청 시 InvalidRefreshTokenException이 발생한다")
    fun refresh_unknownToken_fails() {
        // given: 존재하지 않는 토큰 조회 시 빈 Mono 반환 모킹
        `when`(refreshTokenRepository.findByTokenId("missing-token")).thenReturn(Mono.empty())

        // when & then: InvalidRefreshTokenException 예외 발생 검증
        StepVerifier
            .create(service.refresh("missing-token"))
            .expectError(InvalidRefreshTokenException::class.java)
            .verify()

        // 토큰이 유효하지 않으므로 삭제 동작이 실행되지 않아야 함
        verify(refreshTokenRepository, never()).deleteByTokenId(anyValue())
    }

    @Test
    @DisplayName("로그아웃 시 리프레시 토큰을 데이터베이스에서 삭제한다")
    fun logout_deletesRefreshToken() {
        // given
        `when`(refreshTokenRepository.deleteByTokenId("token-1")).thenReturn(Mono.empty())

        // when & then
        StepVerifier.create(service.logout("token-1")).verifyComplete()

        // 로그아웃 요청으로 해당 리프레시 토큰 무효화 확인
        verify(refreshTokenRepository).deleteByTokenId("token-1")
    }
}
