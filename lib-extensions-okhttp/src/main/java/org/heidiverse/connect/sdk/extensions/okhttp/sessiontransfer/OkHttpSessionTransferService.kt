package org.heidiverse.connect.sdk.extensions.okhttp.sessiontransfer

import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.heidiverse.connect.sdk.di.HeidentityConnectKoinComponent
import org.heidiverse.connect.sdk.dpop.DpopKeyRef
import org.heidiverse.connect.sdk.dpop.DpopNonceTarget
import org.heidiverse.connect.sdk.dpop.DpopProofProvider
import org.heidiverse.connect.sdk.exception.DeserializationException
import org.heidiverse.connect.sdk.exception.HeidentityConnectException
import org.heidiverse.connect.sdk.exception.NetworkException
import org.heidiverse.connect.sdk.exception.ResponseException
import org.heidiverse.connect.sdk.exception.UnauthorizedException
import org.heidiverse.connect.sdk.extensions.okhttp.extensions.fromNetworkCall
import org.heidiverse.connect.sdk.extensions.sessiontransfer.SessionTransferErrorType
import org.heidiverse.connect.sdk.extensions.sessiontransfer.networking.SessionTransferService
import org.heidiverse.connect.sdk.extensions.sessiontransfer.networking.token.SessionTransferTokenRequestBody
import org.heidiverse.connect.sdk.extensions.sessiontransfer.networking.token.SessionTransferTokenResponseBody
import org.heidiverse.connect.sdk.tokenhandling.TokenHandler
import org.koin.core.component.inject

class OkHttpSessionTransferService(
	private val httpClient: OkHttpClient,
	private val json: Json,
) : SessionTransferService, HeidentityConnectKoinComponent {

	/** Resolved on first use, since this service can be constructed before the SDK is initialized. */
	private val dpopProofProvider by inject<DpopProofProvider>()

	companion object {
		private val CONTENT_TYPE_APPLICATION_JSON = "application/json".toMediaType()

		private const val HEADER_AUTHORIZATION = "Authorization"
		private const val BEARER_TOKEN_PREFIX = "Bearer"
	}

	override suspend fun createSessionTransferToken(
		sttUrl: String,
		tokenHandler: TokenHandler,
		request: SessionTransferTokenRequestBody,
	): String {
		if (!tokenHandler.isAuthorized(false)) {
			throw UnauthorizedException()
		}
		try {
			val tokenResult = tokenHandler.getBearerToken(false, false)
			val bearerToken = tokenResult.bearerToken.getOrElse {
				throw it
			}

			// This request does not go through AuthorizationHeaderInterceptor, so it carries its own scheme handling and
			// DPoP retry. Without that, a DPoP-bound access token would be rejected here.
			return executeRequest(sttUrl, request, bearerToken, tokenResult.dpopKeyRef)
		} catch (e: Exception) {
			throw HeidentityConnectException.fromNetworkCall(e, SessionTransferErrorType.SESSION_TRANSFER)
		}
	}

	private suspend fun executeRequest(
		sttUrl: String,
		request: SessionTransferTokenRequestBody,
		bearerToken: String,
		dpopKeyRef: DpopKeyRef?,
		isDpopRetry: Boolean = false,
	): String {
		val body = json.encodeToString(SessionTransferTokenRequestBody.serializer(), request)

		val httpRequest = Request.Builder()
			.url(sttUrl)
			.apply {
				if (dpopKeyRef != null) {
					addHeader(
						HEADER_AUTHORIZATION,
						"${DpopProofProvider.AUTHORIZATION_SCHEME_DPOP} $bearerToken",
					)
					addHeader(
						DpopProofProvider.HEADER_DPOP,
						dpopProofProvider.createProof(
							keyRef = dpopKeyRef,
							httpMethod = "POST",
							url = sttUrl,
							target = DpopNonceTarget.RESOURCE_SERVER,
							accessToken = bearerToken,
						),
					)
				} else {
					addHeader(HEADER_AUTHORIZATION, "$BEARER_TOKEN_PREFIX $bearerToken")
				}
			}
			.post(body.toRequestBody(CONTENT_TYPE_APPLICATION_JSON))
			.build()

		httpClient.newCall(httpRequest).execute().use { response ->
			if (dpopKeyRef != null) {
				val needsDpopRetry = dpopProofProvider.handleResponse(
					target = DpopNonceTarget.RESOURCE_SERVER,
					url = sttUrl,
					statusCode = response.code,
					headerValue = { response.header(it) },
				)
				if (needsDpopRetry && !isDpopRetry) {
					return executeRequest(sttUrl, request, bearerToken, dpopKeyRef, isDpopRetry = true)
				}
			}

			if (!response.isSuccessful) {
				val responseBody = response.body?.string()
				val message = "Server returned error 'HTTP ${response.code}' for ${response.request.method} ${response.request.url.encodedPath}"
				throw ResponseException(
					SessionTransferErrorType.SESSION_TRANSFER,
					response.code,
					message,
					headers = response.headers.toMultimap(),
					body = responseBody,
				)
			}
			val responseBody = response.body?.string()
				?: throw IllegalStateException("Empty session transfer token response")
			return json.decodeFromString(SessionTransferTokenResponseBody.serializer(), responseBody).token
		}
	}
}
