package org.heidiverse.connect.sdk.networking.token

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Response body according to the IETF OAuth 2.0 [specification](https://datatracker.ietf.org/doc/html/rfc6749#section-5.1) and OIDC [specification](https://openid.net/specs/openid-connect-core-1_0.html#TokenResponse)
 */
@Serializable
data class TokenResponseBody(
	@SerialName("access_token")
	val accessToken: String,

	@SerialName("token_type")
	val tokenType: String,

	@SerialName("expires_in")
	val expiresIn: Long? = null,

	@SerialName("refresh_token")
	val refreshToken: String? = null,

	@SerialName("scope")
	val scope: String? = null,

	@SerialName("id_token")
	val idToken: String? = null,
)
