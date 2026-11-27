package org.heidiverse.connect.sdk.sample.shared

import org.heidiverse.connect.sdk.dpop.DpopAlgorithm
import org.heidiverse.connect.sdk.dpop.DpopConfig
import org.heidiverse.connect.sdk.dpop.DpopMode

/**
 * The DPoP configuration the sample apps start with. Everything except the algorithms can be changed in the app, in
 * the DPoP section of the IDP config.
 *
 * Set this to null to start with DPoP turned off:
 * `internal val sampleDpopConfig: DpopConfig? = null`
 */
internal val sampleDpopConfig: DpopConfig? = DpopConfig(

	// ENFORCED: the authorization server has to issue DPoP-bound tokens. If it answers with a token_type other than
	// DPoP, or this device cannot create a key, the login fails with a DpopException. In Keycloak, the client needs
	// its DPoP-bound access tokens setting enabled for this to work.
	// OPPORTUNISTIC: DPoP is used where the server and the device support it, and falls back to plain bearer tokens
	// otherwise. Each fallback is logged as OidcEvent.DpopNotUsed.
	mode = DpopMode.ENFORCED,

	// The signature algorithms to offer, in order of preference. ES256 is currently the only one, since it is what
	// both the Android Keystore and the iOS Secure Enclave support. Not editable in the app for that reason.
	supportedAlgorithms = listOf(DpopAlgorithm.ES256),

	// Allow a software-backed key when the device has no secure element. True here because Android emulators usually
	// have none and the iOS simulator has no Secure Enclave, which would otherwise make every login there fail with
	// HARDWARE_BACKING_UNAVAILABLE.
	// Keep this false in a real app: without a hardware-backed key, the private key can be extracted, which is the
	// whole point of sender-constraining the tokens.
	allowSoftwareKeyFallback = true,
)

/** The base for the DPoP config edited in the app, also when DPoP is turned off */
internal val defaultDpopConfig: DpopConfig
	get() = sampleDpopConfig ?: DpopConfig()
