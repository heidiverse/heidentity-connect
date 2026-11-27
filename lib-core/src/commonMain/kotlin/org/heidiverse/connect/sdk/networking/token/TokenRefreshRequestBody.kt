package org.heidiverse.connect.sdk.networking.token

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Request body according to IETF OAuth 2.0 [specification](https://datatracker.ietf.org/doc/html/rfc6749#section-6)
 */
@Serializable
internal data class TokenRefreshRequestBody(
	@SerialName("grant_type")
	val grantType: String = "refresh_token",

	@SerialName("refresh_token")
	val refreshToken: String,

	@SerialName("scope")
	val scope: String? = null,
)
