package org.heidiverse.connect.sdk.ktor.exception

import io.ktor.client.engine.darwin.DarwinHttpRequestException
import org.heidiverse.connect.sdk.exception.PlatformNetworkError

internal actual fun platformErrorFromException(e: Exception): PlatformNetworkError? {
	return when (e) {
		is DarwinHttpRequestException -> e.origin
		else -> null
	}
}
