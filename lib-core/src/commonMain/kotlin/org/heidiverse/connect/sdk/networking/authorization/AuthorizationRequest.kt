package org.heidiverse.connect.sdk.networking.authorization

import com.eygraber.uri.Url
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import org.heidiverse.connect.sdk.data.OpenIdDefaults
import org.heidiverse.connect.sdk.extensions.appendOptionalQueryParameter
import org.heidiverse.connect.sdk.networking.authorization.AuthorizationRequest.*
import org.heidiverse.connect.sdk.pkce.CodeChallenge
import org.heidiverse.connect.sdk.util.RandomGenerator
import kotlin.io.encoding.ExperimentalEncodingApi

/**
 * Request body according to the IETF OAuth 2.0 [specification](https://datatracker.ietf.org/doc/html/rfc6749#section-4.1.1)
 */
@OptIn(ExperimentalEncodingApi::class)
@Serializable(with = AuthorizationRequestSerializer::class)
sealed interface AuthorizationRequest {

	val responseType: String
	val clientId: String
	val redirectUri: String?
	val scope: String?
	val state: String?

	fun toUrl(
		authorizationEndpoint: String,
		codeChallenge: CodeChallenge?,
		customQueryParameters: Map<String, String> = emptyMap(),
	): String

	/**
	 * Request body according to the OIDC [specification](https://openid.net/specs/openid-connect-core-1_0.html#AuthRequest)
	 */
	@Serializable
	data class Code(
		@SerialName("client_id")
		override val clientId: String,

		@SerialName("redirect_uri")
		override val redirectUri: String,

		@SerialName("native_callback_uri")
		val nativeCallbackUri: String? = null,

		@SerialName("scope")
		override val scope: String,

		@SerialName("state")
		override val state: String? = RandomGenerator.generateRandomString(),

		@SerialName("response_mode")
		val responseMode: String? = null,

		@SerialName("nonce")
		val nonce: String? = RandomGenerator.generateRandomString(),

		@SerialName("display")
		val display: String? = null,

		@SerialName("prompt")
		val prompt: String? = null,

		@SerialName("max_age")
		val maxAge: Int? = null,

		@SerialName("ui_locales")
		val uiLocales: String? = null,

		@SerialName("id_token_hint")
		val idTokenHint: String? = null,

		@SerialName("login_hint")
		val loginHint: String? = null,

		@SerialName("acr_values")
		val acrValues: String? = null,
	) : AuthorizationRequest {

		@SerialName("response_type")
		override val responseType: String = OpenIdDefaults.ResponseType.CODE

		override fun toUrl(
			authorizationEndpoint: String,
			codeChallenge: CodeChallenge?,
			customQueryParameters: Map<String, String>,
		): String {
			return Url.parse(authorizationEndpoint).buildUpon().apply {
				// OAuth 2.0 Authorization Code flow parameters
				appendQueryParameter("response_type", responseType)
				appendQueryParameter("scope", scope)
				appendQueryParameter("client_id", clientId)
				appendQueryParameter("redirect_uri", redirectUri)
				appendOptionalQueryParameter("state", state)

				// OAuth 2.0 parameters
				appendOptionalQueryParameter("response_mode", responseMode)

				// OIDC 1.0 parameters
				appendOptionalQueryParameter("nonce", nonce)
				appendOptionalQueryParameter("display", display)
				appendOptionalQueryParameter("prompt", prompt)
				appendOptionalQueryParameter("max_age", maxAge?.toString())
				appendOptionalQueryParameter("ui_locales", uiLocales)
				appendOptionalQueryParameter("id_token_hint", idTokenHint)
				appendOptionalQueryParameter("login_hint", loginHint)
				appendOptionalQueryParameter("acr_values", acrValues)

				// PKCE parameters
				appendOptionalQueryParameter("code_challenge", codeChallenge?.codeChallenge)
				appendOptionalQueryParameter("code_challenge_method", codeChallenge?.codeChallengeMethod?.identifier)

				// Native OAuth
				appendOptionalQueryParameter("native_callback_uri", nativeCallbackUri)

				// Custom query parameters
				customQueryParameters.forEach { (key, value) ->
					appendQueryParameter(key, value)
				}
			}.build().toString()
		}
	}

	// TODO Not yet supported
	@Serializable
	data class Implicit(
		@SerialName("response_type") override val responseType: String,
		@SerialName("client_id") override val clientId: String,
		@SerialName("redirect_uri") override val redirectUri: String? = null,
		@SerialName("scope") override val scope: String? = null,
		@SerialName("state") override val state: String? = null,
	) : AuthorizationRequest {
		override fun toUrl(
			authorizationEndpoint: String,
			codeChallenge: CodeChallenge?,
			customQueryParameters: Map<String, String>,
		): String {
			TODO("Not yet implemented")
		}
	}

	// TODO Not yet supported
	@Serializable
	data class Hybrid(
		@SerialName("response_type") override val responseType: String,
		@SerialName("client_id") override val clientId: String,
		@SerialName("redirect_uri") override val redirectUri: String? = null,
		@SerialName("scope") override val scope: String? = null,
		@SerialName("state") override val state: String? = null,
	) : AuthorizationRequest {
		override fun toUrl(
			authorizationEndpoint: String,
			codeChallenge: CodeChallenge?,
			customQueryParameters: Map<String, String>,
		): String {
			TODO("Not yet implemented")
		}
	}
}

object AuthorizationRequestSerializer : KSerializer<AuthorizationRequest> {
	@Serializable
	@SerialName("AuthorizationRequest")
	private class Surrogate(
		@SerialName("response_type") val responseType: String?,
		@SerialName("client_id") val clientId: String,
		@SerialName("redirect_uri") val redirectUri: String? = null,
		@SerialName("scope") val scope: String? = null,
		@SerialName("state") val state: String? = null,

		@SerialName("native_callback_uri") val nativeCallbackUri: String? = null,
		@SerialName("response_mode") val responseMode: String? = null,
		@SerialName("nonce") val nonce: String? = null,
		@SerialName("display") val display: String? = null,
		@SerialName("prompt") val prompt: String? = null,
		@SerialName("max_age") val maxAge: Int? = null,
		@SerialName("ui_locales") val uiLocales: String? = null,
		@SerialName("id_token_hint") val idTokenHint: String? = null,
		@SerialName("login_hint") val loginHint: String? = null,
		@SerialName("acr_values") val acrValues: String? = null,
	)

	override val descriptor: SerialDescriptor = Surrogate.serializer().descriptor

	override fun deserialize(decoder: Decoder): AuthorizationRequest {
		val surrogate = decoder.decodeSerializableValue(Surrogate.serializer())

		return when {
			surrogate.responseType == null ||
					surrogate.responseType == OpenIdDefaults.ResponseType.CODE -> Code(
				clientId = surrogate.clientId,
				redirectUri = surrogate.redirectUri ?: throw SerializationException("redirect_uri is missing"),
				scope = surrogate.scope ?: throw SerializationException("scope is missing"),
				state = surrogate.state,
				nativeCallbackUri = surrogate.nativeCallbackUri,
				responseMode = surrogate.responseMode,
				nonce = surrogate.nonce,
				display = surrogate.display,
				prompt = surrogate.prompt,
				maxAge = surrogate.maxAge,
				uiLocales = surrogate.uiLocales,
				idTokenHint = surrogate.idTokenHint,
				loginHint = surrogate.loginHint,
				acrValues = surrogate.acrValues
			)
			surrogate.responseType.contains(OpenIdDefaults.ResponseType.CODE) -> Hybrid(
				responseType = surrogate.responseType,
				clientId = surrogate.clientId,
				redirectUri = surrogate.redirectUri,
				scope = surrogate.scope,
				state = surrogate.state
			)
			else -> Implicit(
				responseType = surrogate.responseType,
				clientId = surrogate.clientId,
				redirectUri = surrogate.redirectUri,
				scope = surrogate.scope,
				state = surrogate.state
			)
		}
	}

	override fun serialize(encoder: Encoder, value: AuthorizationRequest) {
		val surrogate = when (value) {
			is Code -> Surrogate(
				responseType = value.responseType,
				clientId = value.clientId,
				redirectUri = value.redirectUri,
				scope = value.scope,
				state = value.state,
				nativeCallbackUri = value.nativeCallbackUri,
				responseMode = value.responseMode,
				nonce = value.nonce,
				display = value.display,
				prompt = value.prompt,
				maxAge = value.maxAge,
				uiLocales = value.uiLocales,
				idTokenHint = value.idTokenHint,
				loginHint = value.loginHint,
				acrValues = value.acrValues
			)
			is Implicit -> Surrogate(
				responseType = value.responseType,
				clientId = value.clientId,
				redirectUri = value.redirectUri,
				scope = value.scope,
				state = value.state
			)
			is Hybrid -> Surrogate(
				responseType = value.responseType,
				clientId = value.clientId,
				redirectUri = value.redirectUri,
				scope = value.scope,
				state = value.state
			)
		}
		encoder.encodeSerializableValue(Surrogate.serializer(), surrogate)
	}
}
