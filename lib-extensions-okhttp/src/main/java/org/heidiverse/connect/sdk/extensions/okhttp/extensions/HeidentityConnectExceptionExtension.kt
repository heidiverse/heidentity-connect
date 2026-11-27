package org.heidiverse.connect.sdk.extensions.okhttp.extensions

import kotlinx.serialization.SerializationException
import org.heidiverse.connect.sdk.exception.DeserializationException
import org.heidiverse.connect.sdk.exception.HeidentityConnectErrorType
import org.heidiverse.connect.sdk.exception.HeidentityConnectException
import org.heidiverse.connect.sdk.exception.NetworkException

fun HeidentityConnectException.Companion.fromNetworkCall(e: Exception, errorType: HeidentityConnectErrorType): HeidentityConnectException {
	return when (e) {
		is HeidentityConnectException -> e
		is SerializationException -> DeserializationException(
			errorType,
			e.message,
			e,
		)
		else -> NetworkException(errorType, e.message, e, e)
	}
}