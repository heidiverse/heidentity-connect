package org.heidiverse.connect.sdk.networking

import org.heidiverse.connect.sdk.data.AuthTokens
import org.heidiverse.connect.sdk.data.IdpConfig
import org.heidiverse.connect.sdk.dpop.DpopKeyRef
import org.heidiverse.connect.sdk.exception.AuthorizationErrorType

interface AuthorizationService {

	companion object {
		const val WELL_KNOWN_PATH = "/.well-known"
		const val DISCOVERY_PATH = "$WELL_KNOWN_PATH/openid-configuration"
	}

	suspend fun doDiscoveryRequest(
		baseUrl: String,
		customHeaders: Map<String, String> = emptyMap()
	): String

	/**
	 * @param dpopKeyRef when set, the request carries a DPoP proof signed with this key. The proof is what binds both the
	 *   issued refresh token and — if the server honors it — the access token to the key.
	 */
	suspend fun doTokenExchangeRequest(
		tokenEndpoint: String,
		idpConfig: IdpConfig,
		authorizationCode: String,
		codeVerifier: String?,
		customHeaders: Map<String, String> = emptyMap(),
		dpopKeyRef: DpopKeyRef? = null,
	): AuthTokens

	/**
	 * @param dpopKeyRef when set, the request carries a DPoP proof signed with this key. This must be passed whenever the
	 *   stored tokens reference a key, independently of whether the *access* token is bound — a DPoP-bound refresh token
	 *   presented without a proof is rejected with `invalid_grant`.
	 */
	suspend fun doTokenRefreshRequest(
		tokenEndpoint: String,
		idpConfig: IdpConfig,
		refreshToken: String,
		customHeaders: Map<String, String> = emptyMap(),
		dpopKeyRef: DpopKeyRef? = null,
	): AuthTokens

	suspend fun mapTokenRequestException(errorType: AuthorizationErrorType, e: Exception): Exception
}
