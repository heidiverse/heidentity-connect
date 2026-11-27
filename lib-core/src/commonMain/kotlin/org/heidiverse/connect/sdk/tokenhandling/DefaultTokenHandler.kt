package org.heidiverse.connect.sdk.tokenhandling

import org.heidiverse.connect.sdk.configuration.OidcConfiguration
import org.heidiverse.connect.sdk.data.AuthTokens
import org.heidiverse.connect.sdk.data.IdpConfig
import org.heidiverse.connect.sdk.di.HeidentityConnectKoinComponent
import org.heidiverse.connect.sdk.repository.AuthorizationRepository
import org.koin.core.component.inject

class DefaultTokenHandler(
	private val idpConfig: IdpConfig,
	private val bearerTokenProvider: (AuthTokens) -> String? = { it.accessToken },
) : TokenHandler, HeidentityConnectKoinComponent {

	private val authRepository by inject<AuthorizationRepository>()
	private val logger by lazy { inject<OidcConfiguration>().value.logger }

	private val handlerImpl by lazy {
		TokenHandlerImpl(
			idpConfig = idpConfig,
			bearerTokenProvider = bearerTokenProvider,
			authRepository = authRepository,
			logger = logger,
			tokenRefresher = TokenRefresher(authRepository, logger)
		)
	}

	override suspend fun isAuthorized(
		usedDuringTokenVerification: Boolean,
	) = handlerImpl.isAuthorized(usedDuringTokenVerification)

	override suspend fun getAccessAndIdTokens(
		usedDuringTokenVerification: Boolean,
		forceTokenRefresh: Boolean,
	) = handlerImpl.getAccessAndIdTokens(usedDuringTokenVerification, forceTokenRefresh)

	override suspend fun getBearerToken(
		usedDuringTokenVerification: Boolean,
		forceTokenRefresh: Boolean,
	) = handlerImpl.getBearerToken(usedDuringTokenVerification, forceTokenRefresh)

}
