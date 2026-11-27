package org.heidiverse.connect.sdk.extensions.ktor.extensions

import io.ktor.client.plugins.ClientRequestException
import io.ktor.client.plugins.HttpRequestTimeoutException
import io.ktor.client.plugins.ServerResponseException
import io.ktor.util.network.UnresolvedAddressException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.io.IOException
import kotlinx.serialization.SerializationException
import org.heidiverse.connect.sdk.exception.DeserializationException
import org.heidiverse.connect.sdk.exception.HeidentityConnectErrorType
import org.heidiverse.connect.sdk.exception.HeidentityConnectException
import org.heidiverse.connect.sdk.exception.NetworkException
import org.heidiverse.connect.sdk.exception.NetworkTimeoutException
import org.heidiverse.connect.sdk.exception.ResponseException

fun HeidentityConnectException.Companion.fromNetworkCall(e: Exception, errorType: HeidentityConnectErrorType): HeidentityConnectException {
	return when (e) {
		is HeidentityConnectException -> e
		is ClientRequestException, is ServerResponseException -> {
			val response = e.response
			ResponseException(
				errorType,
				response.status.value,
				"Server returned error 'HTTP ${response.status.value}' for ${response.call.request.method.value} ${response.call.request.url.encodedPath}",
				e,
				platformErrorFromException(e),
				response.headers.entries().associate { it.key to it.value },
				null,
			)
		}
		is SerializationException -> DeserializationException(errorType, e.message, e)
		is HttpRequestTimeoutException, is TimeoutCancellationException -> NetworkTimeoutException(
			errorType,
			e.message,
			e,
			platformErrorFromException(e),
		)
		is UnresolvedAddressException, is IOException -> NetworkException(
			errorType,
			e.message,
			e,
			platformErrorFromException(e),
		)
		else -> HeidentityConnectException(errorType, e.message, e)
	}
}