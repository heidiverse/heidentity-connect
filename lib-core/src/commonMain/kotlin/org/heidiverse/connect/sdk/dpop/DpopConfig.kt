package org.heidiverse.connect.sdk.dpop

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Configuration for [DPoP (RFC 9449)](https://datatracker.ietf.org/doc/html/rfc9449) sender-constrained tokens.
 * Set this on [org.heidiverse.connect.sdk.data.IdpConfig] to enable DPoP for a provider. Without a config, DPoP is off.
 */
@Serializable
data class DpopConfig(
	/** Whether DPoP is required, or only used where the device and the server support it. See [DpopMode]. */
	val mode: DpopMode = DpopMode.ENFORCED,

	/**
	 * Signature algorithms to use, in order of preference. Negotiated against the provider's
	 * `dpop_signing_alg_values_supported` and against what the platform's secure element can do.
	 */
	val supportedAlgorithms: List<DpopAlgorithm> = listOf(DpopAlgorithm.ES256),

	/**
	 * Allow falling back to a software-backed key when no secure element is available (Android emulators, iOS
	 * simulator, devices without a TEE). Off by default: without a hardware-backed key, DPoP no longer protects
	 * against exfiltration of the private key, which is the entire point of sender-constraining the tokens.
	 */
	val allowSoftwareKeyFallback: Boolean = false,
)

/**
 * How strictly DPoP is applied.
 */
@Serializable
enum class DpopMode {
	/**
	 * Use DPoP where possible, and fall back to plain bearer tokens otherwise: when this device cannot create a suitable
	 * key (no secure element with [DpopConfig.allowSoftwareKeyFallback] off, or no algorithm in common with the
	 * provider), or when the authorization server answers with a `token_type` other than `DPoP`. Every fallback is
	 * logged as [org.heidiverse.connect.sdk.logging.OidcEvent.DpopNotUsed].
	 *
	 * Meant for rolling DPoP out before every server or client configuration issues DPoP-bound tokens. A bearer token
	 * can only be the server's choice (TLS prevents a downgrade on the wire), so this protects against nothing an
	 * attacker can do — it only tolerates servers and devices that are not ready.
	 */
	@SerialName("opportunistic")
	OPPORTUNISTIC,

	/**
	 * Require DPoP. Authorization fails with a [DpopException] if no suitable key can be created, and token responses
	 * with a `token_type` other than `DPoP` are rejected with [DpopException.Reason.TOKEN_NOT_DPOP_BOUND].
	 */
	@SerialName("enforced")
	ENFORCED,
}

/**
 * DPoP JWS signature algorithms supported by this SDK.
 *
 * Only ES256 is available: the iOS Secure Enclave supports P-256 ECDSA and nothing else, so it is the only algorithm
 * that can satisfy the hardware-backing requirement on both platforms. More entries can be added here as platform
 * support allows.
 */
@Serializable
enum class DpopAlgorithm(
	/** The `alg` value used in the DPoP proof JWS header. */
	val jwsAlgorithm: String,
	/** The JWK `crv` value of the corresponding key. */
	val curve: String,
) {
	@SerialName("ES256")
	ES256("ES256", "P-256"),
	;

	companion object {
		fun fromJwsAlgorithm(jwsAlgorithm: String): DpopAlgorithm? =
			entries.firstOrNull { it.jwsAlgorithm.equals(jwsAlgorithm, ignoreCase = false) }
	}
}
