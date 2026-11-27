package org.heidiverse.connect.sdk.okhttp.exception

import kotlinx.serialization.SerializationException
import org.heidiverse.connect.sdk.exception.AuthorizationErrorType
import org.heidiverse.connect.sdk.exception.AuthorizationException
import org.heidiverse.connect.sdk.exception.DeserializationException
import org.heidiverse.connect.sdk.exception.HeidentityConnectException
import org.heidiverse.connect.sdk.exception.NetworkException
import java.io.IOException
import java.net.UnknownHostException

internal object OkHttpAuthorizationExceptionFactory {
	fun fromException(type: AuthorizationErrorType, e: Exception): HeidentityConnectException {
		return when (e) {
			is AuthorizationException -> e
			is SerializationException -> DeserializationException(type, e.message, e)
			is UnknownHostException, is IOException -> NetworkException(type, e.message, e)
			else -> AuthorizationException(type, e.message, e)
		}
	}
}
