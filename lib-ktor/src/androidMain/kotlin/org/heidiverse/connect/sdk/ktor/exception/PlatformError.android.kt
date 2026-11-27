package org.heidiverse.connect.sdk.ktor.exception

import org.heidiverse.connect.sdk.exception.PlatformNetworkError

internal actual fun platformErrorFromException(e: Exception): PlatformNetworkError? = null
