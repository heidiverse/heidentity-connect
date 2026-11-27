package org.heidiverse.connect.sdk.networking.token

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Request body according to IETF OAuth 2.0 [specification](https://datatracker.ietf.org/doc/html/rfc6749#section-4.1.3)
 */
@Serializable
internal data class TokenExchangeRequestBody(
	@SerialName("grant_type")
	val grantType: String,

	@SerialName("code")
	val code: String,

	@SerialName("redirect_uri")
	val redirectUri: String? = null,

	@SerialName("client_id")
	val clientId: String? = null,
)
