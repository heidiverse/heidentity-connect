package org.heidiverse.connect.sdk.dpop

import org.heidiverse.connect.sdk.logging.OidcEvent
import org.heidiverse.connect.sdk.logging.OidcEventLogger

internal class RecordingEventLogger : OidcEventLogger {
	val events = mutableListOf<OidcEvent>()

	override fun logEvent(event: OidcEvent) {
		events += event
	}

	inline fun <reified T : OidcEvent> count(): Int = events.count { it is T }
}
