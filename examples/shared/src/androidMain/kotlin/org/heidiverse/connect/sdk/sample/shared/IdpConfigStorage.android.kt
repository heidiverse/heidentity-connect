package org.heidiverse.connect.sdk.sample.shared

import android.content.Context
import android.content.SharedPreferences
import org.heidiverse.connect.sdk.data.IdpConfig
import org.heidiverse.connect.sdk.sample.shared.IdpConfigDefaults.KEY_CLIENT_ID
import org.heidiverse.connect.sdk.sample.shared.IdpConfigDefaults.KEY_CLIENT_SECRET
import org.heidiverse.connect.sdk.sample.shared.IdpConfigDefaults.KEY_DISCOVERY_URL
import org.heidiverse.connect.sdk.sample.shared.IdpConfigDefaults.KEY_DPOP_ALLOW_SOFTWARE_KEY
import org.heidiverse.connect.sdk.sample.shared.IdpConfigDefaults.KEY_DPOP_ENABLED
import org.heidiverse.connect.sdk.sample.shared.IdpConfigDefaults.KEY_DPOP_MODE
import org.heidiverse.connect.sdk.sample.shared.IdpConfigDefaults.KEY_NATIVE_CALLBACK_URI
import org.heidiverse.connect.sdk.sample.shared.IdpConfigDefaults.KEY_REDIRECT_URI

actual class IdpConfigStorage(context: Context) {

	private companion object {
		const val PREFS_NAME = "idp_preferences"
	}

	private val preferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

	actual var idpConfig: IdpConfig
		get() = IdpConfigDefaults.idpConfigOrDefault(
			discoveryUrl = preferences.getString(KEY_DISCOVERY_URL, null),
			redirectUri = preferences.getString(KEY_REDIRECT_URI, null),
			clientId = preferences.getString(KEY_CLIENT_ID, null),
			clientSecret = preferences.getString(KEY_CLIENT_SECRET, null),
			nativeCallbackUri = preferences.getString(KEY_NATIVE_CALLBACK_URI, null),
			dpop = IdpConfigDefaults.dpopConfigOrDefault(
				enabled = preferences.getBooleanOrNull(KEY_DPOP_ENABLED),
				mode = preferences.getString(KEY_DPOP_MODE, null),
				allowSoftwareKeyFallback = preferences.getBooleanOrNull(KEY_DPOP_ALLOW_SOFTWARE_KEY),
			),
		)
		set(value) {
			val dpop = value.dpop
			val editor = preferences.edit()
				.putString(KEY_DISCOVERY_URL, value.discoveryUrl)
				.putString(KEY_REDIRECT_URI, value.redirectUri)
				.putString(KEY_CLIENT_ID, value.clientId)
				.putString(KEY_CLIENT_SECRET, value.clientSecret)
				.putString(KEY_NATIVE_CALLBACK_URI, value.nativeCallbackUri)
				.putBoolean(KEY_DPOP_ENABLED, dpop != null)
			if (dpop != null) {
				editor
					.putString(KEY_DPOP_MODE, dpop.mode.name)
					.putBoolean(KEY_DPOP_ALLOW_SOFTWARE_KEY, dpop.allowSoftwareKeyFallback)
			}
			editor.apply()
		}

	private fun SharedPreferences.getBooleanOrNull(key: String): Boolean? =
		if (contains(key)) getBoolean(key, false) else null

}
