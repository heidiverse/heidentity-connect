package org.heidiverse.connect.sdk.networking.authorization

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Response body according to the IETF OAuth 2.0 specification, the fields depend on the flow:
 * - [Authorization Code flow](https://datatracker.ietf.org/doc/html/rfc6749#section-4.1.2)
 * - [Implicit flow](https://datatracker.ietf.org/doc/html/rfc6749#section-4.2.2)
 * - [Hybrid flow](https://datatracker.ietf.org/doc/html/rfc6749#section-4.1.2)
 */
@Serializable
internal data class AuthorizationResponseBody(

	/**
	 * Required in Authorization Code flow and Hybrid flow
	 */
	@SerialName("code")
	val code: String? = null,

	/**
	 * Required in Authorization Code flow and Implicit flow
	 */
	@SerialName("state")
	val state: String? = null,

	/**
	 * Required in Implicit flow and Hybrid flow
	 */
	@SerialName("access_token")
	val accessToken: String? = null,

	/**
	 * Required in Hybrid flow
	 */
	@SerialName("id_token")
	val idToken: String? = null,

	/**
	 * Required in Implicit flow
	 */
	@SerialName("token_type")
	val tokenType: String? = null,

	/**
	 * Recommended in Implicit flow
	 */
	@SerialName("expires_in")
	val expiresInSeconds: Long? = null,

	/**
	 * Optional in Implicit flow
	 */
	@SerialName("scope")
	val scope: String? = null,
)
