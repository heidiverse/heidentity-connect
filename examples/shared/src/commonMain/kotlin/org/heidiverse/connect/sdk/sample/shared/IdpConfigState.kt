package org.heidiverse.connect.sdk.sample.shared

import org.heidiverse.connect.sdk.data.IdpConfig
import org.heidiverse.connect.sdk.dpop.DpopMode

/**
 * The IDP configuration as shown in the sample UI. An empty [clientSecret] means that the client has no secret, an
 * empty [nativeCallbackUri] that the App2App login is not available.
 *
 * The DPoP options keep their values while [dpopEnabled] is off, so they can be shown and edited before turning DPoP
 * on. [dpopEnforced] selects [DpopMode.ENFORCED] over [DpopMode.OPPORTUNISTIC]. The DPoP fields are plain booleans
 * rather than SDK types, like everything else here, so they can be passed through Swift export as they are.
 */
data class IdpConfigState(
	val discoveryUrl: String,
	val redirectUri: String,
	val clientId: String,
	val clientSecret: String,
	val nativeCallbackUri: String,
	val dpopEnabled: Boolean,
	val dpopEnforced: Boolean,
	val dpopAllowSoftwareKeyFallback: Boolean,
) {

	/** The DPoP signature algorithms, for display only: they come from [sampleDpopConfig] and are not editable */
	val dpopAlgorithms: String
		get() = defaultDpopConfig.supportedAlgorithms.joinToString { it.jwsAlgorithm }

}

internal fun IdpConfig.toState(): IdpConfigState {
	val dpopShown = dpop ?: defaultDpopConfig
	return IdpConfigState(
		discoveryUrl = discoveryUrl,
		redirectUri = redirectUri,
		clientId = clientId,
		clientSecret = clientSecret.orEmpty(),
		nativeCallbackUri = nativeCallbackUri.orEmpty(),
		dpopEnabled = dpop != null,
		dpopEnforced = dpopShown.mode == DpopMode.ENFORCED,
		dpopAllowSoftwareKeyFallback = dpopShown.allowSoftwareKeyFallback,
	)
}

internal fun IdpConfigState.toIdpConfig() = IdpConfig(
	discoveryUrl = discoveryUrl.trim(),
	redirectUri = redirectUri.trim(),
	clientId = clientId.trim(),
	clientSecret = clientSecret.trim().takeIf { it.isNotEmpty() },
	nativeCallbackUri = nativeCallbackUri.trim().takeIf { it.isNotEmpty() },
	dpop = if (dpopEnabled) {
		defaultDpopConfig.copy(
			mode = if (dpopEnforced) DpopMode.ENFORCED else DpopMode.OPPORTUNISTIC,
			allowSoftwareKeyFallback = dpopAllowSoftwareKeyFallback,
		)
	} else {
		null
	},
)
