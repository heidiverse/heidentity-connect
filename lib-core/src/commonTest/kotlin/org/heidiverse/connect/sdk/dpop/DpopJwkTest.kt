package org.heidiverse.connect.sdk.dpop

import kotlin.test.Test
import kotlin.test.assertEquals

class DpopJwkTest {

	companion object {
		/** The ES256 key from [RFC 7515 Appendix A.3.1](https://datatracker.ietf.org/doc/html/rfc7515#appendix-A.3.1). */
		private val TEST_KEY = DpopJwk(
			keyType = DpopJwk.KEY_TYPE_EC,
			curve = "P-256",
			x = "f83OJ3D2xF1Bg8vub9tLe1gHMzV76e8Tus9uPHvRVEU",
			y = "x_FEzRu9m36HLN_tue659LNpXW6pCyStikYjKIWI5a0",
		)
	}

	/**
	 * The RFC 7638 §3.1 construction: only the required members, lexicographic order, no whitespace. A wrong thumbprint
	 * never matches the `cnf.jkt` in the issued token, so every resource request would fail with a 401.
	 */
	@Test
	fun thumbprintMatchesRfc7638Construction() {
		assertEquals("oKIywvGUpTVTyxMQ3bwIIeQUudfr_CkLMjCE19ECD-U", TEST_KEY.thumbprint())
	}

	@Test
	fun thumbprintIsUnpaddedBase64Url() {
		val thumbprint = TEST_KEY.thumbprint()
		// A SHA-256 digest is 32 bytes, which is 43 base64 characters without padding
		assertEquals(43, thumbprint.length)
		assertEquals(false, thumbprint.contains('='))
		assertEquals(false, thumbprint.contains('+'))
		assertEquals(false, thumbprint.contains('/'))
	}

	@Test
	fun thumbprintIsStableAndDependsOnEveryMember() {
		assertEquals(TEST_KEY.thumbprint(), TEST_KEY.copy().thumbprint())

		val otherCurve = TEST_KEY.copy(curve = "P-384").thumbprint()
		val otherX = TEST_KEY.copy(x = "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA").thumbprint()
		val otherY = TEST_KEY.copy(y = "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA").thumbprint()

		assertEquals(3, setOf(otherCurve, otherX, otherY).size)
		assertEquals(false, setOf(otherCurve, otherX, otherY).contains(TEST_KEY.thumbprint()))
	}
}
