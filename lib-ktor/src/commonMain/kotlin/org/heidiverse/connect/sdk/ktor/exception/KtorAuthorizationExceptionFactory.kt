package org.heidiverse.connect.sdk.ktor.exception

import io.ktor.client.plugins.ClientRequestException
import io.ktor.client.plugins.HttpRequestTimeoutException
import io.ktor.client.plugins.ServerResponseException
import io.ktor.http.Headers
import io.ktor.util.network.UnresolvedAddressException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.io.IOException
import kotlinx.serialization.SerializationException
import org.heidiverse.connect.sdk.exception.AuthorizationErrorType
import org.heidiverse.connect.sdk.exception.AuthorizationException
import org.heidiverse.connect.sdk.exception.DeserializationException
import org.heidiverse.connect.sdk.exception.HeidentityConnectException
import org.heidiverse.connect.sdk.exception.NetworkException
import org.heidiverse.connect.sdk.exception.NetworkTimeoutException
import org.heidiverse.connect.sdk.exception.ResponseException
import io.ktor.client.plugins.ResponseException as KtorResponseException

internal object KtorAuthorizationExceptionFactory {
	fun fromException(type: AuthorizationErrorType, e: Exception): HeidentityConnectException {
		return when (e) {
			is ClientRequestException, is ServerResponseException -> responseException(type, e as KtorResponseException)
			is SerializationException -> DeserializationException(type, e.message, e)
			is HttpRequestTimeoutException, is TimeoutCancellationException -> NetworkTimeoutException(
				type,
				e.message,
				e,
				platformErrorFromException(e),
			)
			is UnresolvedAddressException, is IOException -> NetworkException(
				type,
				e.message,
				e,
				platformErrorFromException(e),
			)
			else -> AuthorizationException(type, e.message, e)
		}
	}

	private fun headersToMap(headers: Headers): Map<String, List<String>> {
		return headers.entries().associate { it.key to it.value }
	}

	private fun responseException(type: AuthorizationErrorType, e: KtorResponseException): ResponseException {
		val response = e.response
		return ResponseException(
			type,
			response.status.value,
			"Server returned error 'HTTP ${response.status.value}' for ${response.call.request.method.value} ${response.call.request.url.encodedPath}",
			e,
			platformErrorFromException(e),
			headersToMap(response.headers),
			null,
		)
	}
}
