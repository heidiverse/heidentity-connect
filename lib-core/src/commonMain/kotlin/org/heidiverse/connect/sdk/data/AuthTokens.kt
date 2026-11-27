package org.heidiverse.connect.sdk.data

import kotlinx.serialization.Serializable
import org.heidiverse.connect.sdk.dpop.DpopKeyRef
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

@Serializable
data class AuthTokens(
	val accessToken: String,
	val idToken: String?,
	val refreshToken: String?,

	/** Expiration timestamp in milliseconds, based on the token response time plus the expires_in value */
	val expiresAt: Long?,

	/**
	 * The `token_type` from the token response. `DPoP` means the access token is sender-constrained and must be
	 * presented with an `Authorization: DPoP` header plus a proof; anything else (normally `Bearer`) means a plain
	 * bearer token. Null for tokens obtained before this field existed.
	 */
	val tokenType: String? = null,

	/**
	 * The DPoP key these tokens are bound to, if any. Present whenever the token request carried a DPoP proof. See
	 * [isDpopBound] for whether resource requests need a proof.
	 */
	val dpopKey: DpopKeyRef? = null,
) {
	companion object {
		private const val TOKEN_TYPE_DPOP = "DPoP"
		/**
		 * Safety period in milliseconds within which a soon-to-be-expired token should already be considered expired.
		 * This ensures that we have enough time to refresh the token before it actually expires.
		 */
		const val TOKEN_EXPIRATION_SAFETY_PERIOD = 30_000L
	}

	/**
	 * Checks if the tokens are expired, taking into account an optional [safetyPeriod], defaulting to 30 seconds.
	 * If the actual [expiresAt] timestamp is less than [safetyPeriod] after now, the tokens are considered expired.
	 *
	 * @return True if the tokens are expired or about to expire within the [safetyPeriod]. False if they are still valid or no [expiresAt] data is available
	 */
	@OptIn(ExperimentalTime::class)
	fun isExpired(safetyPeriod: Long = TOKEN_EXPIRATION_SAFETY_PERIOD): Boolean {
		return expiresAt?.let {
			val now = Clock.System.now().toEpochMilliseconds()
			now > (it - safetyPeriod)
		} ?: false
	}

	/**
	 * True if the *access* token is DPoP-bound and therefore has to be presented as `Authorization: DPoP` together with
	 * a proof carrying the `ath` claim.
	 *
	 * Decided by the server's `token_type`. With DPoP enforced, tokens for which this would be false despite a proof
	 * having been sent are rejected when they are issued. In opportunistic mode they are accepted: the access token is
	 * then a plain bearer token, while the refresh token may still be bound to [dpopKey].
	 */
	fun isDpopBound(): Boolean = dpopKey != null && tokenType.equals(TOKEN_TYPE_DPOP, ignoreCase = true)
}
