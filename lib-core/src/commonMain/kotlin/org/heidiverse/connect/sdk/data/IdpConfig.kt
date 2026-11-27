package org.heidiverse.connect.sdk.data

import kotlinx.serialization.Serializable
import org.heidiverse.connect.sdk.dpop.DpopConfig

@Serializable
data class IdpConfig(
	val discoveryUrl: String,
	val redirectUri: String,
	val clientId: String,
	val clientSecret: String? = null,
	val nativeCallbackUri: String? = null,

	/**
	 * Enables [DPoP (RFC 9449)](https://datatracker.ietf.org/doc/html/rfc9449) sender-constrained tokens for this
	 * provider. Null (the default) keeps using plain bearer tokens.
	 */
	val dpop: DpopConfig? = null,
)
