package org.heidiverse.connect.sdk.sample.shared

import org.heidiverse.connect.sdk.data.IdpConfig
import org.heidiverse.connect.sdk.sample.shared.IdpConfigDefaults.KEY_CLIENT_ID
import org.heidiverse.connect.sdk.sample.shared.IdpConfigDefaults.KEY_CLIENT_SECRET
import org.heidiverse.connect.sdk.sample.shared.IdpConfigDefaults.KEY_DISCOVERY_URL
import org.heidiverse.connect.sdk.sample.shared.IdpConfigDefaults.KEY_DPOP_ALLOW_SOFTWARE_KEY
import org.heidiverse.connect.sdk.sample.shared.IdpConfigDefaults.KEY_DPOP_ENABLED
import org.heidiverse.connect.sdk.sample.shared.IdpConfigDefaults.KEY_DPOP_MODE
import org.heidiverse.connect.sdk.sample.shared.IdpConfigDefaults.KEY_NATIVE_CALLBACK_URI
import org.heidiverse.connect.sdk.sample.shared.IdpConfigDefaults.KEY_REDIRECT_URI
import platform.Foundation.NSUserDefaults

actual class IdpConfigStorage {

	private val defaults = NSUserDefaults.standardUserDefaults

	actual var idpConfig: IdpConfig
		get() = IdpConfigDefaults.idpConfigOrDefault(
			discoveryUrl = defaults.stringForKey(KEY_DISCOVERY_URL),
			redirectUri = defaults.stringForKey(KEY_REDIRECT_URI),
			clientId = defaults.stringForKey(KEY_CLIENT_ID),
			clientSecret = defaults.stringForKey(KEY_CLIENT_SECRET),
			nativeCallbackUri = defaults.stringForKey(KEY_NATIVE_CALLBACK_URI),
			dpop = IdpConfigDefaults.dpopConfigOrDefault(
				enabled = boolOrNull(KEY_DPOP_ENABLED),
				mode = defaults.stringForKey(KEY_DPOP_MODE),
				allowSoftwareKeyFallback = boolOrNull(KEY_DPOP_ALLOW_SOFTWARE_KEY),
			),
		)
		set(value) {
			defaults.setObject(value.discoveryUrl, KEY_DISCOVERY_URL)
			defaults.setObject(value.redirectUri, KEY_REDIRECT_URI)
			defaults.setObject(value.clientId, KEY_CLIENT_ID)
			if (value.clientSecret != null) {
				defaults.setObject(value.clientSecret, KEY_CLIENT_SECRET)
			} else {
				defaults.removeObjectForKey(KEY_CLIENT_SECRET)
			}
			if (value.nativeCallbackUri != null) {
				defaults.setObject(value.nativeCallbackUri, KEY_NATIVE_CALLBACK_URI)
			} else {
				defaults.removeObjectForKey(KEY_NATIVE_CALLBACK_URI)
			}

			val dpop = value.dpop
			defaults.setBool(dpop != null, KEY_DPOP_ENABLED)
			if (dpop != null) {
				defaults.setObject(dpop.mode.name, KEY_DPOP_MODE)
				defaults.setBool(dpop.allowSoftwareKeyFallback, KEY_DPOP_ALLOW_SOFTWARE_KEY)
			}
		}

	private fun boolOrNull(key: String): Boolean? =
		if (defaults.objectForKey(key) != null) defaults.boolForKey(key) else null

}
