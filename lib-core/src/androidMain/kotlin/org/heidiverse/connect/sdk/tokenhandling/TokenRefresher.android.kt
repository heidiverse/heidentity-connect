package org.heidiverse.connect.sdk.tokenhandling

import org.heidiverse.connect.sdk.data.AuthTokens
import org.heidiverse.connect.sdk.data.IdpConfig
import org.heidiverse.connect.sdk.logging.OidcEventLogger
import org.heidiverse.connect.sdk.repository.AuthorizationRepository

internal actual class TokenRefresher actual constructor(
	private val authorizationRepository: AuthorizationRepository,
	private val logger: OidcEventLogger,
) {
	actual suspend fun refreshTokens(idpConfig: IdpConfig): Result<AuthTokens> {
		return authorizationRepository.refreshTokens(idpConfig)
	}
}