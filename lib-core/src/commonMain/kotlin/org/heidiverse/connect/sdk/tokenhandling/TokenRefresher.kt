package org.heidiverse.connect.sdk.tokenhandling

import org.heidiverse.connect.sdk.data.AuthTokens
import org.heidiverse.connect.sdk.data.IdpConfig
import org.heidiverse.connect.sdk.logging.OidcEventLogger
import org.heidiverse.connect.sdk.repository.AuthorizationRepository

internal expect class TokenRefresher(
	authorizationRepository: AuthorizationRepository,
	logger: OidcEventLogger,
) {

	suspend fun refreshTokens(idpConfig: IdpConfig): Result<AuthTokens>

}