package org.heidiverse.connect.sdk.configuration

import kotlin.time.Duration.Companion.seconds

data class HttpClientConfiguration(
	val requestTimeoutMillis: Long = 30.seconds.inWholeMilliseconds,
	val userAgent: String = "HeidentityConnect",
)
