package org.heidiverse.connect.sdk.ktor.exception

import org.heidiverse.connect.sdk.exception.PlatformNetworkError

internal expect fun platformErrorFromException(e: Exception): PlatformNetworkError?
