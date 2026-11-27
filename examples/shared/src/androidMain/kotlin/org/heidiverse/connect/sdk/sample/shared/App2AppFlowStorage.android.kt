package org.heidiverse.connect.sdk.sample.shared

import android.content.Context

actual class App2AppFlowStorage(context: Context) {

	private companion object {
		const val PREFS_NAME = "app2app_preferences"
		const val KEY_SAVED_AUTH_FLOW = "KEY_SAVED_AUTH_FLOW"
	}

	private val preferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

	actual var savedAuthFlow: String?
		get() = preferences.getString(KEY_SAVED_AUTH_FLOW, null)
		set(value) {
			// Committed synchronously: the flow is saved right before the other app is opened, and the process may be
			// killed soon after
			preferences.edit().putString(KEY_SAVED_AUTH_FLOW, value).commit()
		}

}
