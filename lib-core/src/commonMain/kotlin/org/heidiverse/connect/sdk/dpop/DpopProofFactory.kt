package org.heidiverse.connect.sdk.dpop

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.heidiverse.connect.sdk.util.BASE64_URL
import org.heidiverse.connect.sdk.util.RandomGenerator
import org.heidiverse.connect.sdk.util.Sha256
import kotlin.io.encoding.ExperimentalEncodingApi

/**
 * Assembles DPoP proof JWTs per [RFC 9449 §4.2](https://datatracker.ietf.org/doc/html/rfc9449#section-4.2).
 *
 * Pure common code apart from delegating the actual signature and the public key to [DpopKeyStore].
 */
@OptIn(ExperimentalEncodingApi::class)
internal class DpopProofFactory(
	private val keyStore: DpopKeyStore,
	private val clock: DpopClock = DpopClock(),
) {

	companion object {
		private const val TYP_DPOP_PROOF = "dpop+jwt"

		/** Entropy of the `jti` claim. 16 bytes is well above the "sufficiently large" the RFC asks for. */
		private const val JTI_ENTROPY_BYTES = 16

		private val json = Json
	}

	/**
	 * Creates a signed DPoP proof.
	 *
	 * @param httpMethod the HTTP method of the request the proof is for (`htm`)
	 * @param url the target URI, normalized into `htu`
	 * @param accessToken when set, adds the `ath` claim binding the proof to this exact token. Required by the RFC
	 *   whenever the proof accompanies an access token, and must be omitted at the token endpoint.
	 * @param nonce the most recent nonce supplied by this server, if any
	 */
	fun createProof(
		keyRef: DpopKeyRef,
		httpMethod: String,
		url: String,
		accessToken: String?,
		nonce: String?,
	): String {
		val jwk = keyStore.getPublicJwk(keyRef)

		val header = buildJsonObject {
			put("typ", TYP_DPOP_PROOF)
			put("alg", keyRef.algorithm.jwsAlgorithm)
			put(
				"jwk",
				buildJsonObject {
					put("crv", jwk.curve)
					put("kty", jwk.keyType)
					put("x", jwk.x)
					put("y", jwk.y)
				},
			)
		}

		val payload = buildJsonObject {
			put("jti", RandomGenerator.generateRandomString(bytes = JTI_ENTROPY_BYTES))
			put("htm", httpMethod.uppercase())
			put("htu", DpopHtu.normalize(url))
			// Corrected for the server's clock, see DpopClock
			put("iat", clock.nowEpochSeconds())
			if (accessToken != null) {
				put("ath", accessTokenHash(accessToken))
			}
			if (nonce != null) {
				put("nonce", nonce)
			}
		}

		val signingInput = "${header.encodeToBase64Url()}.${payload.encodeToBase64Url()}"
		val signature = keyStore.sign(keyRef, signingInput.encodeToByteArray())

		return "$signingInput.${BASE64_URL.encode(signature)}"
	}

	/** The `ath` claim: base64url(SHA-256(ASCII bytes of the access token)), per RFC 9449 §4.2. */
	private fun accessTokenHash(accessToken: String): String =
		BASE64_URL.encode(Sha256.digest(accessToken.encodeToByteArray()))

	private fun JsonObject.encodeToBase64Url(): String =
		BASE64_URL.encode(json.encodeToString(JsonObject.serializer(), this).encodeToByteArray())
}
