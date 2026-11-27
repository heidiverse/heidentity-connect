package org.heidiverse.connect.sdk.logging

import android.util.Log

internal class DefaultAndroidEventLogger : OidcEventLogger {
	override fun logEvent(event: OidcEvent) {
		Log.d("OIDC", event.toString())
	}
}