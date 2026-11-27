package org.heidiverse.connect.sdk.okhttp.networking

import kotlinx.serialization.json.Json
import okhttp3.*
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.heidiverse.connect.sdk.HeidentityConnect
import org.heidiverse.connect.sdk.client.ClientAuthentication
import org.heidiverse.connect.sdk.data.AuthTokens
import org.heidiverse.connect.sdk.data.IdpConfig
import org.heidiverse.connect.sdk.dpop.DpopKeyRef
import org.heidiverse.connect.sdk.dpop.DpopNonceTarget
import org.heidiverse.connect.sdk.dpop.DpopProofProvider
import org.heidiverse.connect.sdk.exception.AuthorizationErrorType
import org.heidiverse.connect.sdk.exception.ResponseException
import org.heidiverse.connect.sdk.exception.SessionExpiredException
import org.heidiverse.connect.sdk.networking.AuthorizationService
import org.heidiverse.connect.sdk.networking.token.TokenErrorResponse
import org.heidiverse.connect.sdk.networking.token.TokenErrorType
import org.heidiverse.connect.sdk.networking.token.TokenResponseBody
import org.heidiverse.connect.sdk.okhttp.exception.OkHttpAuthorizationExceptionFactory
import kotlin.time.Duration.Companion.seconds

class OkHttpAuthorizationService(
	private val httpClient: OkHttpClient,
	private val json: Json,
	/**
	 * Resolved lazily: the service is constructed before the dependency graph exists, so this cannot be a resolved
	 * instance. Also the seam that lets the nonce-retry tests substitute a fake.
	 */
	private val dpopProofProvider: () -> DpopProofProvider = { HeidentityConnect.getDpopProofProvider() },
) : AuthorizationService {

	override suspend fun doDiscoveryRequest(
		baseUrl: String,
		customHeaders: Map<String, String>
	): String {
		val discoveryUrl = baseUrl.toHttpUrl().newBuilder().encodedPath(AuthorizationService.DISCOVERY_PATH).build()

		val request = Request.Builder()
			.url(discoveryUrl)
			.get()
			.apply {
				for ((key, value) in customHeaders) {
					addHeader(key, value)
				}
			}
			.build()

		httpClient.newCall(request).execute().use { response ->
			return response.mapOrThrow(AuthorizationErrorType.DISCOVERY) { it.body.string() }
		}
	}

	override suspend fun doTokenExchangeRequest(
		tokenEndpoint: String,
		idpConfig: IdpConfig,
		authorizationCode: String,
		codeVerifier: String?,
		customHeaders: Map<String, String>,
		dpopKeyRef: DpopKeyRef?,
	): AuthTokens {
		val clientAuthentication = idpConfig.clientSecret?.let {
			ClientAuthentication.Basic(idpConfig.clientId, it)
		} ?: ClientAuthentication.None(idpConfig.clientId)

		val formFields = buildMap {
			put("grant_type", "authorization_code")
			put("code", authorizationCode)
			put("redirect_uri", idpConfig.redirectUri)

			if (codeVerifier != null) {
				put("code_verifier", codeVerifier)
			}
		}

		val tokenEndpointUrl = tokenEndpoint.toHttpUrl()
		return doTokenRequest(
			tokenEndpointUrl,
			clientAuthentication,
			formFields,
			AuthorizationErrorType.AUTHORIZATION_CODE_EXCHANGE,
			customHeaders,
			dpopKeyRef,
		)
	}

	override suspend fun doTokenRefreshRequest(
		tokenEndpoint: String,
		idpConfig: IdpConfig,
		refreshToken: String,
		customHeaders: Map<String, String>,
		dpopKeyRef: DpopKeyRef?,
	): AuthTokens {
		val clientAuthentication = idpConfig.clientSecret?.let {
			ClientAuthentication.Basic(idpConfig.clientId, it)
		} ?: ClientAuthentication.None(idpConfig.clientId)

		val formFields = buildMap {
			put("grant_type", "refresh_token")
			put("refresh_token", refreshToken)
		}

		val tokenEndpointUrl = tokenEndpoint.toHttpUrl()
		return doTokenRequest(
			tokenEndpointUrl,
			clientAuthentication,
			formFields,
			AuthorizationErrorType.TOKEN_REFRESH,
			customHeaders,
			dpopKeyRef,
		)
	}

	// TODO Registration, Userinfo & End Session Requests?

	/**
	 * @param isDpopRetry guards the single retry allowed after a DPoP rejection (nonce challenge or clock skew), so a
	 *   server that keeps rejecting proofs cannot make this loop.
	 */
	private suspend fun doTokenRequest(
		tokenEndpoint: HttpUrl,
		authentication: ClientAuthentication,
		formFields: Map<String, String>,
		errorType: AuthorizationErrorType,
		customHeaders: Map<String, String>,
		dpopKeyRef: DpopKeyRef?,
		isDpopRetry: Boolean = false,
	): AuthTokens {
		// No ath claim at the token endpoint: no access token is being presented there
		val dpopProof = dpopKeyRef?.let {
			dpopProofProvider().createProof(
				keyRef = it,
				httpMethod = "POST",
				url = tokenEndpoint.toString(),
				target = DpopNonceTarget.AUTHORIZATION_SERVER,
			)
		}

		val headers = Headers.Builder().apply {
			val authHeaders = authentication.getRequestHeaders()
			authHeaders.forEach { (key, value) -> add(key, value) }
			customHeaders.forEach { (key, value) -> add(key, value) }
			dpopProof?.let { add(DpopProofProvider.HEADER_DPOP, it) }
		}.build()

		val body = FormBody.Builder().apply {
			val authParams = authentication.getRequestParameters()
			formFields.forEach { (key, value) -> add(key, value) }
			authParams.forEach { (key, value) -> add(key, value) }
		}.build()

		val request = Request.Builder()
			.url(tokenEndpoint)
			.post(body)
			.headers(headers)
			.build()

		httpClient.newCall(request).execute().use { response ->
			// Read the body once: mapOrThrow consumes it, and DPoP rejections are identified from it
			val rawBody = response.body.string()

			if (dpopKeyRef != null) {
				val needsDpopRetry = dpopProofProvider().handleResponse(
					target = DpopNonceTarget.AUTHORIZATION_SERVER,
					url = tokenEndpoint.toString(),
					statusCode = response.code,
					headerValue = { response.header(it) },
					body = rawBody,
				)
				if (needsDpopRetry && !isDpopRetry) {
					return doTokenRequest(
						tokenEndpoint, authentication, formFields, errorType, customHeaders, dpopKeyRef,
						isDpopRetry = true,
					)
				}
			}

			return response.mapOrThrow(errorType, rawBody) {
				val responseBody = json.decodeFromString<TokenResponseBody>(rawBody)

				// Calculate the token expiration based on the response "expiresIn" field
				val tokenExpiresAt = responseBody.expiresIn?.let {
					response.receivedResponseAtMillis + it.seconds.inWholeMilliseconds
				}

				AuthTokens(
					accessToken = responseBody.accessToken,
					idToken = responseBody.idToken,
					refreshToken = responseBody.refreshToken,
					expiresAt = tokenExpiresAt,
					tokenType = responseBody.tokenType,
				)
			}
		}
	}

	override suspend fun mapTokenRequestException(errorType: AuthorizationErrorType, e: Exception): Exception {
		when (e) {
			is ResponseException -> {
				if (e.statusCode == 400) {
					// The error JSON is in `body`; `message` is the human-readable summary built in mapOrThrow
					val errorResponse = e.body?.let { responseBody ->
						runCatching { json.decodeFromString<TokenErrorResponse>(responseBody) }.getOrNull()
					}
					return if (errorResponse?.error == TokenErrorType.INVALID_GRANT) {
						// See https://datatracker.ietf.org/doc/html/rfc6749#section-5.2
						SessionExpiredException(errorResponse)
					} else {
						e
					}
				} else {
					return e
				}
			}
			else -> {
				return OkHttpAuthorizationExceptionFactory.fromException(errorType, e)
			}
		}
	}

	/**
	 * @param preReadBody the response body if the caller already consumed it. An OkHttp response body can only be read
	 *   once, so a caller that needed the body earlier has to hand it over rather than have it read again here.
	 */
	private fun <T> Response.mapOrThrow(
		errorType: AuthorizationErrorType,
		preReadBody: String? = null,
		block: (Response) -> T,
	): T {
		return when (code) {
			in 400..599 -> {
				val responseBody = preReadBody ?: body.string()
				throw ResponseException(
					errorType,
					code,
					"Server returned error 'HTTP $code' for ${request.method} ${request.url.encodedPath}",
					headers = headers.toMultimap(),
					body = responseBody,
				)
			}
			else -> block(this)
		}
	}
}
