package org.heidiverse.connect.sdk.dpop

import org.heidiverse.connect.sdk.util.BASE64_URL
import org.heidiverse.connect.sdk.util.Sha256
import kotlin.io.encoding.ExperimentalEncodingApi

/**
 * The public part of a DPoP key as a JWK (RFC 7517). Deliberately a typed EC key rather than a generic
 * `Map`/`JsonObject`: the RFC 7638 thumbprint requires the members in lexicographic order with no whitespace, and
 * relying on the incidental iteration order of a map for that is a silent-corruption risk — a wrong thumbprint means the
 * `cnf.jkt` in the issued token never matches and every resource request fails with a 401.
 *
 * [x] and [y] are base64url-encoded field elements, left-padded to the curve's field size (32 bytes for P-256) as
 * required by RFC 7518 §6.2.1.2.
 */
internal data class DpopJwk(
	val keyType: String,
	val curve: String,
	val x: String,
	val y: String,
) {

	/**
	 * The JWK SHA-256 thumbprint (`jkt`) per [RFC 7638 §3.1](https://datatracker.ietf.org/doc/html/rfc7638#section-3.1):
	 * base64url(SHA-256(JSON containing only the required members, in lexicographic order, without whitespace)).
	 */
	@OptIn(ExperimentalEncodingApi::class)
	fun thumbprint(): String {
		val canonical = """{"crv":"$curve","kty":"$keyType","x":"$x","y":"$y"}"""
		return BASE64_URL.encode(Sha256.digest(canonical.encodeToByteArray()))
	}

	companion object {
		const val KEY_TYPE_EC = "EC"
	}
}
