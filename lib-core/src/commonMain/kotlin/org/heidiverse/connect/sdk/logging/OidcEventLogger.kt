package org.heidiverse.connect.sdk.logging

interface OidcEventLogger {
	fun logEvent(event: OidcEvent)
}