package org.heidiverse.connect.sdk.tokenhandling

import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.heidiverse.connect.sdk.data.AuthTokens
import org.heidiverse.connect.sdk.data.IdpConfig
import org.heidiverse.connect.sdk.exception.UnauthorizedException
import org.heidiverse.connect.sdk.logging.OidcEvent
import org.heidiverse.connect.sdk.logging.OidcEventLogger
import org.heidiverse.connect.sdk.repository.AuthorizationRepository

internal class TokenHandlerImpl(
	private val idpConfig: IdpConfig,
	private val bearerTokenProvider: (AuthTokens) -> String?,
	private val authRepository: AuthorizationRepository,
	private val logger: OidcEventLogger,
	private val tokenRefresher: TokenRefresher,
) : TokenHandler {

	override suspend fun isAuthorized(usedDuringTokenVerification: Boolean): Boolean {
		return authRepository.isAuthorized() || (usedDuringTokenVerification && authRepository.isTemporaryAuthorized())
	}

	override suspend fun getAccessAndIdTokens(
		usedDuringTokenVerification: Boolean,
		forceTokenRefresh: Boolean,
	): AccessAndIdTokensResult {
		val (didRefresh, authTokensResult) = retrieveAuthTokens(usedDuringTokenVerification, forceTokenRefresh)

		return AccessAndIdTokensResult(
			didTokenRefresh = didRefresh,
			tokens = authTokensResult.map { it.toAccessAndIdTokens() },
		)
	}

	override suspend fun getBearerToken(usedDuringTokenVerification: Boolean, forceTokenRefresh: Boolean): TokenResult {
		val (didRefresh, authTokensResult) = retrieveAuthTokens(usedDuringTokenVerification, forceTokenRefresh)
		val bearerTokenResult = authTokensResult.mapCatching {
			bearerTokenProvider.invoke(it) ?: throw UnauthorizedException()
		}

		return TokenResult(
			didTokenRefresh = didRefresh,
			bearerToken = bearerTokenResult,
			// Driven by the server's token_type. With DPoP enforced, unbound tokens were already rejected when issued.
			dpopKeyRef = authTokensResult.getOrNull()?.takeIf { it.isDpopBound() }?.dpopKey,
		)
	}

	private suspend fun retrieveAuthTokens(
		usedDuringTokenVerification: Boolean,
		forceTokenRefresh: Boolean,
	): Pair<Boolean, Result<AuthTokens>> {
		// Make sure token refreshes are never cancelled, even if the original coroutine is cancelled
		return withContext(NonCancellable) {
			authRepository.tokenRefreshLock.withLock {
				when {
					forceTokenRefresh -> {
						// The client requested a forced token refresh, for example for a request retry
						val tokens = refreshTokens()
						Pair(true, tokens)
					}
					needsTokenRefresh(usedDuringTokenVerification) -> {
						logger.logEvent(OidcEvent.TokenExpired)

						// The token has expired, we need to refresh it
						val tokens = refreshTokens()
						Pair(true, tokens)
					}
					else -> {
						// Token refresh is not required, return current tokens
						val tokens = authRepository.getAuthTokensWithTemporaryFallback(usedDuringTokenVerification)

						val result = tokens?.let { Result.success(it) }
							?: Result.failure(UnauthorizedException())
						Pair(false, result)
					}
				}
			}
		}
	}

	private fun needsTokenRefresh(usedDuringTokenVerification: Boolean): Boolean {
		val tokens = authRepository.getAuthTokensWithTemporaryFallback(usedDuringTokenVerification)
		return tokens == null || tokens.isExpired()
	}

	private suspend fun refreshTokens(): Result<AuthTokens> {
		return tokenRefresher.refreshTokens(idpConfig)
	}

}
