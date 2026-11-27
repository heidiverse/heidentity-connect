package org.heidiverse.connect.sdk.sample.shared

import org.heidiverse.connect.sdk.data.IdpConfig
import org.heidiverse.connect.sdk.dpop.DpopConfig
import org.heidiverse.connect.sdk.dpop.DpopMode

/**
 * Persists the IDP configuration entered in the sample UI
 */
expect class IdpConfigStorage {
	var idpConfig: IdpConfig
}

internal object IdpConfigDefaults {
	val idpConfig = IdpConfig(
		discoveryUrl = "https://heidentity.ubique.ch",
		redirectUri = "org.heidiverse.connect.sdk.sample://oidc/callback",
		clientId = "heidentity-connect-sample",
		nativeCallbackUri = SampleApp2AppConfig.DEFAULT_NATIVE_CALLBACK_URI,
	)

	const val KEY_DISCOVERY_URL = "KEY_IDP_CONFIG_DISCOVERY_URL"
	const val KEY_REDIRECT_URI = "KEY_IDP_CONFIG_REDIRECT_URI"
	const val KEY_CLIENT_ID = "KEY_IDP_CONFIG_CLIENT_ID"
	const val KEY_CLIENT_SECRET = "KEY_IDP_CONFIG_CLIENT_SECRET"
	const val KEY_NATIVE_CALLBACK_URI = "KEY_IDP_CONFIG_NATIVE_CALLBACK_URI"

	// Only what the app can change is stored, the algorithms always come from sampleDpopConfig
	const val KEY_DPOP_ENABLED = "KEY_IDP_CONFIG_DPOP_ENABLED"
	const val KEY_DPOP_MODE = "KEY_IDP_CONFIG_DPOP_MODE"
	const val KEY_DPOP_ALLOW_SOFTWARE_KEY = "KEY_IDP_CONFIG_DPOP_ALLOW_SOFTWARE_KEY"

	/**
	 * The stored IDP config, or the default one as long as nothing has been saved. The DPoP config is stored
	 * separately and applies to the default IDP config as well.
	 */
	fun idpConfigOrDefault(
		discoveryUrl: String?,
		redirectUri: String?,
		clientId: String?,
		clientSecret: String?,
		nativeCallbackUri: String?,
		dpop: DpopConfig?,
	): IdpConfig {
		return if (discoveryUrl != null && redirectUri != null && clientId != null) {
			IdpConfig(
				discoveryUrl = discoveryUrl,
				redirectUri = redirectUri,
				clientId = clientId,
				clientSecret = clientSecret,
				nativeCallbackUri = nativeCallbackUri,
				dpop = dpop,
			)
		} else {
			idpConfig.copy(dpop = dpop)
		}
	}

	/**
	 * The DPoP config as set in the app, or [sampleDpopConfig] as long as nothing has been saved ([enabled] is null).
	 */
	fun dpopConfigOrDefault(
		enabled: Boolean?,
		mode: String?,
		allowSoftwareKeyFallback: Boolean?,
	): DpopConfig? {
		if (enabled == null) return sampleDpopConfig
		if (!enabled) return null

		val base = defaultDpopConfig
		return base.copy(
			mode = mode?.let { stored -> DpopMode.entries.firstOrNull { it.name == stored } } ?: base.mode,
			allowSoftwareKeyFallback = allowSoftwareKeyFallback ?: base.allowSoftwareKeyFallback,
		)
	}
}
