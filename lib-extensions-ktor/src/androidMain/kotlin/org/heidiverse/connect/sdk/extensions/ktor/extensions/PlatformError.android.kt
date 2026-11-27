package org.heidiverse.connect.sdk.extensions.ktor.extensions

import org.heidiverse.connect.sdk.exception.PlatformNetworkError

internal actual fun platformErrorFromException(e: Exception): PlatformNetworkError? = null
