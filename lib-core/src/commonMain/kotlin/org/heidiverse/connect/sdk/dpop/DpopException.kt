package org.heidiverse.connect.sdk.dpop

import org.heidiverse.connect.sdk.exception.HeidentityConnectErrorCode
import org.heidiverse.connect.sdk.exception.HeidentityConnectErrorType
import org.heidiverse.connect.sdk.exception.HeidentityConnectException

class DpopException(
	override val errorType: HeidentityConnectErrorType,
	val reason: Reason,
	message: String? = null,
	cause: Throwable? = null,
) : HeidentityConnectException(
	errorType = errorType,
	message = message ?: reason.name,
	cause = cause,
	errorCode = HeidentityConnectErrorCode.typed(errorType.errorCode, reason.classOffset),
) {

	enum class Reason {
		/** None of the configured algorithms is supported by both the provider and the platform. */
		NO_SUPPORTED_ALGORITHM,

		/** No secure element is available and [DpopConfig.allowSoftwareKeyFallback] is disabled. */
		HARDWARE_BACKING_UNAVAILABLE,

		/** The platform refused to generate the key pair. */
		KEY_GENERATION_FAILED,

		/**
		 * The key referenced by the stored tokens no longer exists. Happens when app data is restored onto another
		 * device or the keystore was invalidated. A DPoP-bound refresh token is unusable without its key, so this is
		 * treated as a session expiry.
		 */
		KEY_MISSING,

		/** The secure element failed to produce a signature. */
		SIGNING_FAILED,

		/**
		 * DPoP is enforced ([DpopMode.ENFORCED]), but the authorization server answered a token request carrying a proof with a `token_type`
		 * other than `DPoP`. The tokens are rejected instead of falling back to plain bearer tokens. This usually means
		 * the client is not configured for DPoP-bound access tokens on the server side.
		 */
		TOKEN_NOT_DPOP_BOUND,
		;

		val classOffset: Int
			get() = when (this) {
				NO_SUPPORTED_ALGORITHM -> HeidentityConnectErrorCode.OFFSET_DPOP_NO_SUPPORTED_ALGORITHM
				HARDWARE_BACKING_UNAVAILABLE -> HeidentityConnectErrorCode.OFFSET_DPOP_HARDWARE_BACKING_UNAVAILABLE
				KEY_GENERATION_FAILED -> HeidentityConnectErrorCode.OFFSET_DPOP_KEY_GENERATION_FAILED
				KEY_MISSING -> HeidentityConnectErrorCode.OFFSET_DPOP_KEY_MISSING
				SIGNING_FAILED -> HeidentityConnectErrorCode.OFFSET_DPOP_SIGNING_FAILED
				TOKEN_NOT_DPOP_BOUND -> HeidentityConnectErrorCode.OFFSET_DPOP_TOKEN_NOT_DPOP_BOUND
			}
	}
}
