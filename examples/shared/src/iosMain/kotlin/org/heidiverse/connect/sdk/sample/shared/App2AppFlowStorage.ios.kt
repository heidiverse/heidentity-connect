package org.heidiverse.connect.sdk.sample.shared

import platform.Foundation.NSUserDefaults

actual class App2AppFlowStorage {

	private companion object {
		const val KEY_SAVED_AUTH_FLOW = "KEY_APP2APP_SAVED_AUTH_FLOW"
	}

	private val defaults = NSUserDefaults.standardUserDefaults

	actual var savedAuthFlow: String?
		get() = defaults.stringForKey(KEY_SAVED_AUTH_FLOW)
		set(value) {
			if (value != null) {
				defaults.setObject(value, KEY_SAVED_AUTH_FLOW)
			} else {
				defaults.removeObjectForKey(KEY_SAVED_AUTH_FLOW)
			}
		}

}
