package org.heidiverse.connect.sdk.ktor.networking

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.ClientRequestException
import io.ktor.client.request.forms.FormDataContent
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.*
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
import org.heidiverse.connect.sdk.ktor.exception.KtorAuthorizationExceptionFactory
import org.heidiverse.connect.sdk.networking.AuthorizationService
import org.heidiverse.connect.sdk.networking.token.TokenErrorResponse
import org.heidiverse.connect.sdk.networking.token.TokenErrorType
import org.heidiverse.connect.sdk.networking.token.TokenResponseBody
import kotlin.time.Duration.Companion.seconds

internal class KtorAuthorizationService(
	private val httpClient: HttpClient,
	/**
	 * Resolved lazily: the service is constructed before the dependency graph exists, so this cannot be a resolved
	 * instance. Also the seam that lets the nonce-retry tests substitute a fake.
	 */
	private val dpopProofProvider: () -> DpopProofProvider = { HeidentityConnect.getDpopProofProvider() },
) : AuthorizationService {

	companion object {
		private val CONTENT_TYPE_FORM_URL_ENCODED = ContentType.parse("application/x-www-form-urlencoded")
	}

	override suspend fun doDiscoveryRequest(
		baseUrl: String,
		customHeaders: Map<String, String>
	): String {
		val discoveryUrl = URLBuilder(baseUrl).apply {
			appendPathSegments(AuthorizationService.DISCOVERY_PATH)
		}.build()

		val response = httpClient.get(discoveryUrl) {
			for ((key, value) in customHeaders) {
				header(key, value)
			}
		}
		return response.bodyAsText()
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

		val tokenEndpointUrl = Url(tokenEndpoint)
		return doTokenRequest(tokenEndpointUrl, clientAuthentication, formFields, customHeaders, dpopKeyRef)
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

		val tokenEndpointUrl = Url(tokenEndpoint)
		return doTokenRequest(tokenEndpointUrl, clientAuthentication, formFields, customHeaders, dpopKeyRef)
	}

	// TODO Registration, Userinfo & End Session Requests?

	/**
	 * @param isDpopRetry guards the single retry allowed after a DPoP rejection (nonce challenge or clock skew), so a
	 *   server that keeps rejecting proofs cannot make this loop.
	 */
	private suspend fun doTokenRequest(
		tokenEndpoint: Url,
		authentication: ClientAuthentication,
		formFields: Map<String, String>,
		customHeaders: Map<String, String>,
		dpopKeyRef: DpopKeyRef?,
		isDpopRetry: Boolean = false,
	): AuthTokens {
		// No ath claim at the token endpoint: no access token is being presented there
		val dpopProof = dpopKeyRef?.let {
			dpopProofProvider().createProof(
				keyRef = it,
				httpMethod = HttpMethod.Post.value,
				url = tokenEndpoint.toString(),
				target = DpopNonceTarget.AUTHORIZATION_SERVER,
			)
		}

		val response = try {
			executeTokenRequest(tokenEndpoint, authentication, formFields, customHeaders, dpopProof)
		} catch (e: ClientRequestException) {
			// The SDK's own client has expectSuccess = true, so a DPoP rejection arrives as an exception
			if (dpopKeyRef != null && needsDpopRetry(e.response, tokenEndpoint) && !isDpopRetry) {
				return doTokenRequest(
					tokenEndpoint, authentication, formFields, customHeaders, dpopKeyRef, isDpopRetry = true,
				)
			}
			throw e
		}

		// An app-supplied HttpClient may have expectSuccess = false, in which case the rejection arrives as a plain
		// response. Detecting it by status and header rather than by exception type covers both. This also records the
		// nonce and clock offset of successful responses.
		if (dpopKeyRef != null && needsDpopRetry(response, tokenEndpoint) && !isDpopRetry) {
			return doTokenRequest(
				tokenEndpoint, authentication, formFields, customHeaders, dpopKeyRef, isDpopRetry = true,
			)
		}

		val responseBody = response.body<TokenResponseBody>()

		// Calculate the token expiration based on the response "expiresIn" field
		val tokenExpiresAt = responseBody.expiresIn?.let {
			response.responseTime.timestamp + it.seconds.inWholeMilliseconds
		}

		return AuthTokens(
			accessToken = responseBody.accessToken,
			idToken = responseBody.idToken,
			refreshToken = responseBody.refreshToken,
			expiresAt = tokenExpiresAt,
			tokenType = responseBody.tokenType,
		)
	}

	private suspend fun executeTokenRequest(
		tokenEndpoint: Url,
		authentication: ClientAuthentication,
		formFields: Map<String, String>,
		customHeaders: Map<String, String>,
		dpopProof: String?,
	): HttpResponse {
		return httpClient.post(tokenEndpoint) {
			contentType(CONTENT_TYPE_FORM_URL_ENCODED)

			val authHeaders = authentication.getRequestHeaders()
			val authParams = authentication.getRequestParameters()

			authHeaders.forEach { (key, value) -> header(key, value) }

			for ((key, value) in customHeaders) {
				header(key, value)
			}

			dpopProof?.let { header(DpopProofProvider.HEADER_DPOP, it) }

			setBody(
				FormDataContent(
					Parameters.build {
						formFields.forEach { (key, value) -> append(key, value) }
						authParams.forEach { (key, value) -> append(key, value) }
					}
				)
			)
		}
	}

	/**
	 * Records the nonce and clock offset the server supplied and reports whether the response rejected the proof in a
	 * way that a new proof can fix. Only touches the body of error responses, so the success path does not consume the
	 * response content.
	 */
	private suspend fun needsDpopRetry(response: HttpResponse, tokenEndpoint: Url): Boolean {
		val body = if (response.status.isSuccess()) {
			null
		} else {
			runCatching { response.bodyAsText() }.getOrNull()
		}

		return dpopProofProvider().handleResponse(
			target = DpopNonceTarget.AUTHORIZATION_SERVER,
			url = tokenEndpoint.toString(),
			statusCode = response.status.value,
			headerValue = { response.headers[it] },
			body = body,
		)
	}

	override suspend fun mapTokenRequestException(errorType: AuthorizationErrorType, e: Exception): Exception {
		when (e) {
			is ClientRequestException -> {
				if (e.response.status == HttpStatusCode.BadRequest) {
					val errorResponse = runCatching { e.response.body<TokenErrorResponse>() }.getOrNull()
					return if (errorResponse?.error == TokenErrorType.INVALID_GRANT) {
						// See https://datatracker.ietf.org/doc/html/rfc6749#section-5.2
						SessionExpiredException(errorResponse)
					} else {
						ResponseException(
							errorType,
							e.response.status.value,
							"Server returned error 'HTTP ${e.response.status.value}' for ${e.response.call.request.method.value} ${e.response.call.request.url.encodedPath}",
							e,
							null,
							e.response.headers.entries().associate { it.key to it.value },
							e.response.bodyAsText(),
						)
					}
				} else {
					return KtorAuthorizationExceptionFactory.fromException(errorType, e)
				}
			}
			else -> {
				return KtorAuthorizationExceptionFactory.fromException(errorType, e)
			}
		}
	}

}
