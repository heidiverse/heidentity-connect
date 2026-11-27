package org.heidiverse.connect.sdk.logging

internal interface OidcEventLoggerFactory {
	fun create(): OidcEventLogger
}