package com.miyou.app.application.auth.service

import com.miyou.app.application.auth.usecase.LogoutUseCase
import com.miyou.app.application.auth.usecase.OAuthLoginUseCase
import com.miyou.app.application.auth.usecase.TokenRefreshUseCase
import com.miyou.app.domain.auth.exception.InvalidRefreshTokenException
import com.miyou.app.domain.auth.model.AuthTokens
import com.miyou.app.domain.auth.model.OAuthAccount
import com.miyou.app.domain.auth.model.OAuthLoginResult
import com.miyou.app.domain.auth.model.RefreshToken
import com.miyou.app.domain.auth.port.JwtIssuer
import com.miyou.app.domain.auth.port.OAuthAccountRepository
import com.miyou.app.domain.auth.port.RefreshTokenRepository
import com.miyou.app.domain.dialogue.model.UserId
import org.springframework.dao.DuplicateKeyException
import org.springframework.stereotype.Service
import reactor.core.publisher.Mono

@Service
class AuthApplicationService(
    private val oAuthAccountRepository: OAuthAccountRepository,
    private val refreshTokenRepository: RefreshTokenRepository,
    private val jwtIssuer: JwtIssuer,
) : OAuthLoginUseCase,
    TokenRefreshUseCase,
    LogoutUseCase {
    override fun loginOrRegister(result: OAuthLoginResult): Mono<AuthTokens> =
        oAuthAccountRepository
            .findByProviderAndProviderUserId(result.provider, result.providerUserId)
            .switchIfEmpty(Mono.defer { registerNewAccount(result) })
            .flatMap { account -> issueTokenPair(account.userId) }

    override fun refresh(refreshTokenValue: String): Mono<AuthTokens> =
        refreshTokenRepository
            .findByTokenId(refreshTokenValue)
            .switchIfEmpty(Mono.error(InvalidRefreshTokenException(refreshTokenValue)))
            .flatMap { old ->
                refreshTokenRepository
                    .deleteByTokenId(old.tokenId)
                    .then(issueTokenPair(old.userId))
            }

    override fun logout(refreshTokenValue: String): Mono<Void> =
        refreshTokenRepository.deleteByTokenId(refreshTokenValue)

    private fun registerNewAccount(result: OAuthLoginResult): Mono<OAuthAccount> {
        val newUserId = UserId.generate()
        return oAuthAccountRepository
            .save(OAuthAccount.create(result, newUserId))
            .onErrorResume(DuplicateKeyException::class.java) {
                oAuthAccountRepository.findByProviderAndProviderUserId(result.provider, result.providerUserId)
            }
    }

    private fun issueTokenPair(userId: UserId): Mono<AuthTokens> {
        val accessToken = jwtIssuer.issueAccessToken(userId)
        return refreshTokenRepository
            .save(RefreshToken.issue(userId))
            .map { saved -> AuthTokens(accessToken.token, accessToken.expiresAt, saved.tokenId) }
    }
}
