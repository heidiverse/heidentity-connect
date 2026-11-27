package org.heidiverse.connect.sdk.networking.token

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Token endpoint error response as described in https://datatracker.ietf.org/doc/html/rfc6749#section-5.2
 */
@Serializable
data class TokenErrorResponse(
	@SerialName("error")
	val error: TokenErrorType,

	@SerialName("error_description")
	val errorDescription: String? = null,

	@SerialName("error_uri")
	val errorUri: String? = null,
)

enum class TokenErrorType {
	@SerialName("invalid_request")
	INVALID_REQUEST,

	@SerialName("invalid_client")
	INVALID_CLIENT,

	@SerialName("invalid_grant")
	INVALID_GRANT,

	@SerialName("unauthorized_client")
	UNAUTHORIZED_CLIENT,

	@SerialName("unsupported_grant_type")
	UNSUPPORTED_GRANT_TYPE,

	@SerialName("invalid_scope")
	INVALID_SCOPE,

	/**
	 * The authorization server requires a nonce in the DPoP proof and supplied one in the `DPoP-Nonce` response header.
	 * @see [RFC 9449 §8](https://datatracker.ietf.org/doc/html/rfc9449#section-8)
	 */
	@SerialName("use_dpop_nonce")
	USE_DPOP_NONCE,

	/**
	 * The DPoP proof was missing, malformed, or did not verify.
	 * @see [RFC 9449 §5](https://datatracker.ietf.org/doc/html/rfc9449#section-5)
	 */
	@SerialName("invalid_dpop_proof")
	INVALID_DPOP_PROOF,
}