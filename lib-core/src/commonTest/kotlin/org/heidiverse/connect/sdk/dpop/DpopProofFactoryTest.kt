package org.heidiverse.connect.sdk.dpop

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.heidiverse.connect.sdk.util.BASE64_URL
import kotlin.io.encoding.ExperimentalEncodingApi
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalEncodingApi::class)
class DpopProofFactoryTest {

	private val keyStore = FakeDpopKeyStore()
	private val factory = DpopProofFactory(keyStore)
	private val keyRef = DpopKeyRef("alias", DpopAlgorithm.ES256, isHardwareBacked = true)

	@Test
	fun headerDeclaresTheProofTypeAndTheKey() {
		val header = proof().header()

		assertEquals("dpop+jwt", header["typ"]?.jsonPrimitive?.content)
		assertEquals("ES256", header["alg"]?.jsonPrimitive?.content)

		val jwk = header["jwk"]?.jsonObject
		assertEquals("EC", jwk?.get("kty")?.jsonPrimitive?.content)
		assertEquals("P-256", jwk?.get("crv")?.jsonPrimitive?.content)
		assertEquals("f83OJ3D2xF1Bg8vub9tLe1gHMzV76e8Tus9uPHvRVEU", jwk?.get("x")?.jsonPrimitive?.content)
		assertEquals("x_FEzRu9m36HLN_tue659LNpXW6pCyStikYjKIWI5a0", jwk?.get("y")?.jsonPrimitive?.content)
	}

	/** A leaked private key would make the whole scheme pointless, so assert the header carries only public members. */
	@Test
	fun headerNeverCarriesPrivateKeyMembers() {
		val jwk = proof().header()["jwk"]?.jsonObject

		assertEquals(setOf("kty", "crv", "x", "y"), jwk?.keys)
	}

	@Test
	fun payloadCarriesTheBoundMethodAndNormalizedUri() {
		val payload = proof(
			httpMethod = "post",
			url = "https://server.example.com/token?state=abc#frag",
		).payload()

		assertEquals("POST", payload["htm"]?.jsonPrimitive?.content)
		assertEquals("https://server.example.com/token", payload["htu"]?.jsonPrimitive?.content)
	}

	@Test
	fun issuedAtIsInSecondsNotMilliseconds() {
		val issuedAt = proof().payload()["iat"]?.jsonPrimitive?.content?.toLong()

		assertTrue(issuedAt != null && issuedAt > 1_700_000_000L, "iat was $issuedAt")
		assertTrue(issuedAt < 100_000_000_000L, "iat looks like milliseconds: $issuedAt")
	}

	/** The `ath` vector from [RFC 9449 §4.3](https://datatracker.ietf.org/doc/html/rfc9449#section-4.3). */
	@Test
	fun accessTokenHashMatchesTheRfcVector() {
		val payload = proof(accessToken = "Kz~8mXK1EalYznwH-LC-1fBAo.4Ljp~zsPE_NeO.gxU").payload()

		assertEquals("fUHyO2r2Z3DZ53EsNrWBb0xWXoaNy59IiKCAqksmQEo", payload["ath"]?.jsonPrimitive?.content)
	}

	@Test
	fun accessTokenHashIsOmittedAtTheTokenEndpoint() {
		assertNull(proof(accessToken = null).payload()["ath"])
	}

	@Test
	fun nonceIsIncludedOnlyWhenAvailable() {
		assertNull(proof(nonce = null).payload()["nonce"])
		assertEquals("eyJ7S_zG", proof(nonce = "eyJ7S_zG").payload()["nonce"]?.jsonPrimitive?.content)
	}

	@Test
	fun jtiIsUniquePerProof() {
		val jtis = List(20) { proof().payload()["jti"]?.jsonPrimitive?.content }

		assertEquals(20, jtis.toSet().size)
		jtis.forEach { assertTrue(it != null && it.isNotEmpty()) }
	}

	@Test
	fun signatureCoversTheEncodedHeaderAndPayload() {
		val proof = proof()
		val parts = proof.split(".")

		assertEquals(3, parts.size)
		assertContentEquals("${parts[0]}.${parts[1]}".encodeToByteArray(), keyStore.signedInputs.last())
		assertEquals(BASE64_URL.encode(ByteArray(64) { it.toByte() }), parts[2])
	}

	@Test
	fun proofIsBase64UrlWithoutPadding() {
		val proof = proof()

		assertTrue(proof.none { it == '=' || it == '+' || it == '/' }, proof)
	}

	@Test
	fun differentRequestsProduceDifferentProofs() {
		assertNotEquals(
			proof(url = "https://api.example.com/a"),
			proof(url = "https://api.example.com/b"),
		)
	}

	private fun proof(
		httpMethod: String = "GET",
		url: String = "https://api.example.com/resource",
		accessToken: String? = null,
		nonce: String? = null,
	): String = factory.createProof(keyRef, httpMethod, url, accessToken, nonce)

	private fun String.header(): JsonObject = decodePart(0)

	private fun String.payload(): JsonObject = decodePart(1)

	private fun String.decodePart(index: Int): JsonObject =
		Json.parseToJsonElement(BASE64_URL.decode(split(".")[index]).decodeToString()).jsonObject
}
