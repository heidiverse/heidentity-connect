package org.heidiverse.connect.sdk.client

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

/**
 * According to OIDC [specification](https://openid.net/specs/openid-connect-core-1_0.html#ClientAuthentication)
 */
sealed interface ClientAuthentication {

	fun getRequestHeaders(): Map<String, String> = emptyMap()

	fun getRequestParameters(): Map<String, String> = emptyMap()

	/**
	 * Client does not have a client secret
	 */
	data class None(val clientId: String) : ClientAuthentication {
		override fun getRequestParameters() = mapOf(
			"client_id" to clientId,
		)
	}

	/**
	 * HTTP Basic authentication
	 */
	data class Basic(
		val clientId: String,
		val clientSecret: String,
	) : ClientAuthentication {
		@OptIn(ExperimentalEncodingApi::class)
		override fun getRequestHeaders(): Map<String, String> {
			val credentials = "$clientId:$clientSecret"
			val encoded = Base64.UrlSafe.encode(credentials.encodeToByteArray())
			return mapOf("Authorization" to "Basic $encoded")
		}
	}

	/**
	 * Client ID and client secret in the request body
	 */
	@Serializable
	data class Post(
		@SerialName("client_id")
		val clientId: String,

		@SerialName("client_secret")
		val clientSecret: String,
	) : ClientAuthentication {
		override fun getRequestParameters() = mapOf(
			"client_id" to clientId,
			"client_secret" to clientSecret,
		)
	}

	// TODO OIDC Spec also defines client_secret_jwt and private_key_jwt as authentication methods

}