package org.heidiverse.connect.sdk.tokenhandling

interface TokenHandler {

	/**
	 * Returns whether the user is authorized. This will only check if [org.heidiverse.connect.sdk.data.AuthTokens] are present and not whether or not they are valid.
	 *
	 * @param usedDuringTokenVerification True if this invocation occurs during a token verification process, which has access to temporary auth tokens
	 *
	 * @return True if the user has authorization tokens persisted, false otherwise
	 */
	suspend fun isAuthorized(usedDuringTokenVerification: Boolean): Boolean

	/**
	 * Gets the current access and ID tokens, refreshing them if necessary.
	 *
	 * @param usedDuringTokenVerification True if this invocation occurs during a token verification process, which has access to temporary auth tokens
	 * @param forceTokenRefresh True if the tokens should be refreshed regardless of their current validity. If false, the handler will only refresh the tokens if they are expired or invalid.
	 *
	 * @return An [AccessAndIdTokensResult] containing the result of the token retrieval and whether a refresh was performed.
	 */
	suspend fun getAccessAndIdTokens(
		usedDuringTokenVerification: Boolean,
		forceTokenRefresh: Boolean,
	): AccessAndIdTokensResult

	/**
	 * Gets the current bearer token, refreshing it if necessary.
	 *
	 * @param usedDuringTokenVerification True if this invocation occurs during a token verification process, which has access to temporary auth tokens
	 * @param forceTokenRefresh True if the token should be refreshed regardless of its current validity. If false, the handler will only refresh the token if it is expired or invalid.
	 *
	 * @return A [TokenResult] containing the result of the token retrieval and whether a refresh was performed.
	 */
	suspend fun getBearerToken(usedDuringTokenVerification: Boolean, forceTokenRefresh: Boolean): TokenResult

}
