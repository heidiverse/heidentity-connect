package org.heidiverse.connect.sdk.logging

sealed class OidcEvent(val severity: OidcEventSeverity) {

	open val correlationId: String? = null

	data class FetchingProviderMetadataFailed(val exception: Exception) : OidcEvent(OidcEventSeverity.ERROR)

	data class AuthorizationCodeExchangeStarted(
		override val correlationId: String? = null,
	) : OidcEvent(OidcEventSeverity.INFO)

	data class AuthorizationCodeExchangeSuccessful(
		val expiresAt: Long,
		override val correlationId: String? = null,
	) : OidcEvent(OidcEventSeverity.INFO)

	data class AuthorizationCodeExchangeFailed(
		val exception: Exception,
		override val correlationId: String? = null,
	) : OidcEvent(OidcEventSeverity.ERROR)

	data object TokenExpired : OidcEvent(OidcEventSeverity.INFO)

	data class TokenRefreshStarted(
		override val correlationId: String? = null,
	) : OidcEvent(OidcEventSeverity.INFO)

	/** Event when tokens were cleared from storage during a token refresh */
	data class TokensClearedDuringRefresh(
		override val correlationId: String? = null,
	) : OidcEvent(OidcEventSeverity.INFO)

	data class TokenRefreshSuccessful(
		val expiresAt: Long,
		override val correlationId: String? = null,
	) : OidcEvent(OidcEventSeverity.INFO)

	data class TokenRefreshFailed(
		val exception: Exception,
		override val correlationId: String? = null,
	) : OidcEvent(OidcEventSeverity.ERROR)

	data class UnauthorizedRequest(
		val requestUrl: String,
		val statusCode: Int,
		val didRefreshTokens: Boolean,
	) : OidcEvent(OidcEventSeverity.ERROR)

	data class DpopKeyGenerated(
		val alias: String,
		val algorithm: String,
		val hardwareBacked: Boolean,
	) : OidcEvent(OidcEventSeverity.INFO)

	/**
	 * A software-backed key was used because no secure element was available and
	 * [org.heidiverse.connect.sdk.dpop.DpopConfig.allowSoftwareKeyFallback] was enabled. The tokens are still
	 * sender-constrained, but the private key is no longer protected against exfiltration.
	 */
	data class DpopKeyNotHardwareBacked(val alias: String) : OidcEvent(OidcEventSeverity.ERROR)

	data class DpopKeyDeletionFailed(val alias: String, val cause: Throwable) : OidcEvent(OidcEventSeverity.ERROR)

	data class DpopOrphanedKeysPruned(val count: Int) : OidcEvent(OidcEventSeverity.INFO)

	data class DpopKeyPruningFailed(val cause: Throwable) : OidcEvent(OidcEventSeverity.ERROR)

	/**
	 * The key referenced by the stored tokens is gone. A DPoP-bound refresh token cannot be used without it, so the
	 * session is treated as expired.
	 */
	data class DpopKeyMissing(val alias: String) : OidcEvent(OidcEventSeverity.ERROR)

	data class DpopNonceRequired(val target: String, val url: String) : OidcEvent(OidcEventSeverity.INFO)

	/**
	 * A DPoP proof was rejected, and the device clock was adjusted to the server's time, which differs by
	 * [offsetSeconds]. Servers reject proofs whose `iat` is more than a few seconds off.
	 */
	data class DpopClockSkewCorrected(val offsetSeconds: Long) : OidcEvent(OidcEventSeverity.INFO)

	/**
	 * DPoP is configured in [org.heidiverse.connect.sdk.dpop.DpopMode.OPPORTUNISTIC] mode, but plain bearer tokens are used
	 * instead, for the given [reason].
	 */
	data class DpopNotUsed(val reason: String) : OidcEvent(OidcEventSeverity.INFO)

	data class DpopProofCreationFailed(val exception: Exception) : OidcEvent(OidcEventSeverity.ERROR)


	/**
	 * Event when the stored auth tokens could not be read, e.g. because they were corrupted or written in an incompatible
	 * format. The stored tokens are removed, so the user is logged out.
	 */
	data class StoredAuthTokensCorrupted(val exception: Exception) : OidcEvent(OidcEventSeverity.ERROR)

	data class KeychainRetrievalError(val key: String, val osStatus: Int) : OidcEvent(OidcEventSeverity.ERROR)
	data class KeychainSaveError(val key: String, val osStatus: Int) : OidcEvent(OidcEventSeverity.ERROR)
	data class KeychainDeleteError(val key: String, val osStatus: Int) : OidcEvent(OidcEventSeverity.ERROR)

	/**
	 * Event when the iOS expiration handler was called before the token refresh has finished.
	 */
	data object IosExpirationHandlerCalled : OidcEvent(OidcEventSeverity.ERROR)

}
