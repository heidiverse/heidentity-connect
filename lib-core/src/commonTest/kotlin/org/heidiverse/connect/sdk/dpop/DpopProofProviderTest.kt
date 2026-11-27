package org.heidiverse.connect.sdk.dpop

import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.heidiverse.connect.sdk.logging.OidcEvent
import org.heidiverse.connect.sdk.util.BASE64_URL
import kotlin.io.encoding.ExperimentalEncodingApi
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

/**
 * The DPoP retry rules live in one place so that the four HTTP call sites cannot drift apart. These tests are what
 * pin those rules down.
 */
@OptIn(ExperimentalTime::class)
class DpopProofProviderTest {

	/** Device time: 2026-01-01T00:00:00Z */
	private val deviceClock = FixedClock(Instant.fromEpochSeconds(DEVICE_EPOCH_SECONDS))
	private val dpopClock = DpopClock(deviceClock)
	private val nonceStore = DpopNonceStore()
	private val logger = RecordingEventLogger()
	private val missingKeys = mutableListOf<DpopKeyRef>()
	private val keyStore = FakeDpopKeyStore()
	private val provider = DpopProofProvider(
		proofFactory = DpopProofFactory(keyStore, dpopClock),
		nonceStore = nonceStore,
		clock = dpopClock,
		logger = logger,
		onKeyMissing = { missingKeys += it },
	)

	private val tokenEndpoint = "https://server.example.com/token"
	private val resourceUrl = "https://api.example.com/resource"

	@Test
	fun authorizationServerChallengeIsDetectedAndTheNonceStored() = runTest {
		val retry = provider.handleResponse(
			target = DpopNonceTarget.AUTHORIZATION_SERVER,
			url = tokenEndpoint,
			statusCode = 400,
			headerValue = { if (it == DpopProofProvider.HEADER_DPOP_NONCE) "as-nonce" else null },
			body = """{"error":"use_dpop_nonce","error_description":"Authorization server requires nonce"}""",
		)

		assertTrue(retry)
		assertEquals("as-nonce", nonceStore.get(DpopNonceTarget.AUTHORIZATION_SERVER, tokenEndpoint))
		assertEquals(1, logger.count<OidcEvent.DpopNonceRequired>())
	}

	@Test
	fun resourceServerChallengeIsDetectedFromTheWwwAuthenticateHeader() = runTest {
		val retry = provider.handleResponse(
			target = DpopNonceTarget.RESOURCE_SERVER,
			url = resourceUrl,
			statusCode = 401,
			headerValue = {
				when (it) {
					DpopProofProvider.HEADER_DPOP_NONCE -> "rs-nonce"
					"WWW-Authenticate" -> """DPoP error="use_dpop_nonce", error_description="Resource server requires nonce""""
					else -> null
				}
			},
		)

		assertTrue(retry)
		assertEquals("rs-nonce", nonceStore.get(DpopNonceTarget.RESOURCE_SERVER, resourceUrl))
	}

	/**
	 * A server may rotate the nonce on a successful response. Storing that is what keeps the *next* request from being
	 * challenged, so it must happen even though no retry is needed.
	 */
	@Test
	fun rotatedNonceOnSuccessIsStoredWithoutRequestingARetry() = runTest {
		val retry = provider.handleResponse(
			target = DpopNonceTarget.RESOURCE_SERVER,
			url = resourceUrl,
			statusCode = 200,
			headerValue = { if (it == DpopProofProvider.HEADER_DPOP_NONCE) "rotated" else null },
		)

		assertFalse(retry)
		assertEquals("rotated", nonceStore.get(DpopNonceTarget.RESOURCE_SERVER, resourceUrl))
		assertEquals(0, logger.count<OidcEvent.DpopNonceRequired>())
	}

	@Test
	fun noNonceHeaderIsNotAChallenge() = runTest {
		val retry = provider.handleResponse(
			target = DpopNonceTarget.AUTHORIZATION_SERVER,
			url = tokenEndpoint,
			statusCode = 400,
			headerValue = { null },
			body = """{"error":"use_dpop_nonce"}""",
		)

		assertFalse(retry)
		assertNull(nonceStore.get(DpopNonceTarget.AUTHORIZATION_SERVER, tokenEndpoint))
	}

	/** An ordinary error must not be mistaken for a nonce challenge, or every failure would be retried. */
	@Test
	fun unrelatedErrorIsNotAChallenge() = runTest {
		val retry = provider.handleResponse(
			target = DpopNonceTarget.AUTHORIZATION_SERVER,
			url = tokenEndpoint,
			statusCode = 400,
			headerValue = { if (it == DpopProofProvider.HEADER_DPOP_NONCE) "nonce" else null },
			body = """{"error":"invalid_grant"}""",
		)

		assertFalse(retry)
		// The nonce is still worth keeping even though this response was not a challenge
		assertEquals("nonce", nonceStore.get(DpopNonceTarget.AUTHORIZATION_SERVER, tokenEndpoint))
	}

	/** The two flavours of the challenge are not interchangeable: a 401 is not how the token endpoint asks. */
	@Test
	fun challengeShapesAreNotCrossDetected() = runTest {
		assertFalse(
			provider.handleResponse(
				target = DpopNonceTarget.AUTHORIZATION_SERVER,
				url = tokenEndpoint,
				statusCode = 401,
				headerValue = { if (it == DpopProofProvider.HEADER_DPOP_NONCE) "nonce" else null },
				body = """{"error":"use_dpop_nonce"}""",
			)
		)
		assertFalse(
			provider.handleResponse(
				target = DpopNonceTarget.RESOURCE_SERVER,
				url = resourceUrl,
				statusCode = 400,
				headerValue = { if (it == DpopProofProvider.HEADER_DPOP_NONCE) "nonce" else null },
			)
		)
	}

	@Test
	fun createdProofPicksUpTheStoredNonce() = runTest {
		provider.handleResponse(
			target = DpopNonceTarget.AUTHORIZATION_SERVER,
			url = tokenEndpoint,
			statusCode = 400,
			headerValue = { if (it == DpopProofProvider.HEADER_DPOP_NONCE) "the-nonce" else null },
			body = """{"error":"use_dpop_nonce"}""",
		)

		val proof = provider.createProof(
			keyRef = DpopKeyRef("alias", DpopAlgorithm.ES256, isHardwareBacked = true),
			httpMethod = "POST",
			url = tokenEndpoint,
			target = DpopNonceTarget.AUTHORIZATION_SERVER,
		)

		assertEquals("the-nonce", proof.nonceClaim())
	}

	/** The nonce of the *other* target must never leak into a proof. */
	@Test
	fun createdProofIgnoresTheOtherTargetsNonce() = runTest {
		nonceStore.store(DpopNonceTarget.RESOURCE_SERVER, tokenEndpoint, "resource-nonce")

		val proof = provider.createProof(
			keyRef = DpopKeyRef("alias", DpopAlgorithm.ES256, isHardwareBacked = true),
			httpMethod = "POST",
			url = tokenEndpoint,
			target = DpopNonceTarget.AUTHORIZATION_SERVER,
		)

		assertNull(proof.nonceClaim())
	}

	@OptIn(ExperimentalEncodingApi::class)
	private fun String.nonceClaim(): String? {
		val payload = Json.parseToJsonElement(BASE64_URL.decode(split(".")[1]).decodeToString()).jsonObject
		return payload["nonce"]?.jsonPrimitive?.content
	}

	/**
	 * A device clock that is off makes the server reject the proof's `iat`. The server's `Date` header reveals the offset,
	 * so the rejection is worth one retry with a corrected proof.
	 */
	@Test
	fun invalidProofWithSkewedClockIsRetriedAndCorrectsIat() = runTest {
		val retry = provider.handleResponse(
			target = DpopNonceTarget.AUTHORIZATION_SERVER,
			url = tokenEndpoint,
			statusCode = 400,
			// Server is five minutes ahead of the device
			headerValue = { if (it == "Date") "Thu, 01 Jan 2026 00:05:00 GMT" else null },
			body = """{"error":"invalid_dpop_proof","error_description":"DPoP proof is not active"}""",
		)

		assertTrue(retry)
		assertEquals(300, dpopClock.offsetSeconds)
		assertEquals(1, logger.count<OidcEvent.DpopClockSkewCorrected>())

		val proof = provider.createProof(keyRef, "POST", tokenEndpoint, DpopNonceTarget.AUTHORIZATION_SERVER)
		assertEquals(DEVICE_EPOCH_SECONDS + 300, proof.claim("iat")?.toLong())
	}

	@Test
	fun resourceServerInvalidProofWithSkewedClockIsRetried() = runTest {
		val retry = provider.handleResponse(
			target = DpopNonceTarget.RESOURCE_SERVER,
			url = resourceUrl,
			statusCode = 401,
			headerValue = {
				when (it) {
					"Date" -> "Wed, 31 Dec 2025 23:58:00 GMT"
					"WWW-Authenticate" -> """DPoP error="invalid_dpop_proof""""
					else -> null
				}
			},
		)

		assertTrue(retry)
		assertEquals(-120, dpopClock.offsetSeconds)
	}

	/** The clock is only adjusted when a proof was rejected, never from ordinary responses. */
	@Test
	fun dateOfSuccessfulResponseIsIgnored() = runTest {
		val retry = provider.handleResponse(
			target = DpopNonceTarget.RESOURCE_SERVER,
			url = resourceUrl,
			statusCode = 200,
			headerValue = { if (it == "Date") "Thu, 01 Jan 2026 01:00:00 GMT" else null },
		)

		assertFalse(retry)
		assertEquals(0, dpopClock.offsetSeconds)
	}

	/** Without the server's time there is nothing to correct, so a retry would fail the same way. */
	@Test
	fun invalidProofWithoutDateHeaderIsNotRetried() = runTest {
		val retry = provider.handleResponse(
			target = DpopNonceTarget.AUTHORIZATION_SERVER,
			url = tokenEndpoint,
			statusCode = 400,
			headerValue = { null },
			body = """{"error":"invalid_dpop_proof"}""",
		)

		assertFalse(retry)
		assertEquals(0, dpopClock.offsetSeconds)
	}

	@Test
	fun malformedDateHeaderIsIgnored() = runTest {
		val retry = provider.handleResponse(
			target = DpopNonceTarget.AUTHORIZATION_SERVER,
			url = tokenEndpoint,
			statusCode = 400,
			headerValue = { if (it == "Date") "not a date" else null },
			body = """{"error":"invalid_dpop_proof"}""",
		)

		assertFalse(retry)
		assertEquals(0, dpopClock.offsetSeconds)
	}

	@Test
	fun missingKeyIsReported() = runTest {
		val missing = DpopKeyRef("gone", DpopAlgorithm.ES256, isHardwareBacked = true)
		keyStore.missingAliases += missing.alias

		assertFailsWith<DpopException> {
			provider.createProof(missing, "GET", resourceUrl, DpopNonceTarget.RESOURCE_SERVER, accessToken = "at")
		}

		assertEquals(listOf(missing), missingKeys)
	}

	@OptIn(ExperimentalEncodingApi::class)
	private fun String.claim(name: String): String? {
		val payload = Json.parseToJsonElement(BASE64_URL.decode(split(".")[1]).decodeToString()).jsonObject
		return payload[name]?.jsonPrimitive?.content
	}

	private val keyRef = DpopKeyRef("alias", DpopAlgorithm.ES256, isHardwareBacked = true)

	private class FixedClock(private val instant: Instant) : Clock {
		override fun now(): Instant = instant
	}

	private companion object {
		const val DEVICE_EPOCH_SECONDS = 1767225600L
	}
}
