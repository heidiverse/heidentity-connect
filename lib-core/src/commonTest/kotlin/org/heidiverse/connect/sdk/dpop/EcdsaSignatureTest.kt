package org.heidiverse.connect.sdk.dpop

import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class EcdsaSignatureTest {

	companion object {
		private const val FIELD_SIZE = 32
		private const val SEQUENCE_TAG: Byte = 0x30
		private const val INTEGER_TAG: Byte = 0x02
	}

	@Test
	fun convertsPlainSignature() {
		val r = ByteArray(FIELD_SIZE) { 0x11 }
		val s = ByteArray(FIELD_SIZE) { 0x22 }

		val jose = EcdsaSignature.derToJose(der(r, s), FIELD_SIZE)

		assertEquals(64, jose.size)
		assertContentEquals(r, jose.copyOfRange(0, FIELD_SIZE))
		assertContentEquals(s, jose.copyOfRange(FIELD_SIZE, 64))
	}

	/**
	 * DER integers are signed, so a component whose high bit is set carries a leading 0x00 that must not end up in the
	 * JOSE output.
	 */
	@Test
	fun stripsTheDerSignByte() {
		val r = ByteArray(FIELD_SIZE) { 0x80.toByte() }
		val s = ByteArray(FIELD_SIZE) { 0x01 }

		val der = der(byteArrayOf(0x00) + r, s)
		val jose = EcdsaSignature.derToJose(der, FIELD_SIZE)

		assertEquals(64, jose.size)
		assertContentEquals(r, jose.copyOfRange(0, FIELD_SIZE))
		assertContentEquals(s, jose.copyOfRange(FIELD_SIZE, 64))
	}

	/** DER omits leading zeros, JOSE requires the full field width, so short components have to be left-padded. */
	@Test
	fun leftPadsShortComponents() {
		val shortR = byteArrayOf(0x07)
		val shortS = byteArrayOf(0x01, 0x02)

		val jose = EcdsaSignature.derToJose(der(shortR, shortS), FIELD_SIZE)

		assertEquals(64, jose.size)
		assertContentEquals(ByteArray(FIELD_SIZE - 1) + byteArrayOf(0x07), jose.copyOfRange(0, FIELD_SIZE))
		assertContentEquals(ByteArray(FIELD_SIZE - 2) + byteArrayOf(0x01, 0x02), jose.copyOfRange(FIELD_SIZE, 64))
	}

	@Test
	fun rejectsMalformedInput() {
		assertFailsWith<IllegalArgumentException> { EcdsaSignature.derToJose(ByteArray(0), FIELD_SIZE) }
		assertFailsWith<IllegalArgumentException> {
			// A JOSE signature is not a DER signature — passing one in by mistake must not silently produce garbage
			EcdsaSignature.derToJose(ByteArray(64) { 0x01 }, FIELD_SIZE)
		}
		assertFailsWith<IllegalArgumentException> {
			// Trailing data beyond the declared SEQUENCE length
			EcdsaSignature.derToJose(der(ByteArray(4), ByteArray(4)) + byteArrayOf(0x00), FIELD_SIZE)
		}
		assertFailsWith<IllegalArgumentException> {
			// A component wider than the field
			EcdsaSignature.derToJose(der(ByteArray(FIELD_SIZE + 1) { 0x01 }, ByteArray(4)), FIELD_SIZE)
		}
	}

	/** Builds `SEQUENCE { INTEGER r, INTEGER s }` with short-form lengths, which is enough for P-256. */
	private fun der(r: ByteArray, s: ByteArray): ByteArray {
		val content = byteArrayOf(INTEGER_TAG, r.size.toByte()) + r + byteArrayOf(INTEGER_TAG, s.size.toByte()) + s
		return byteArrayOf(SEQUENCE_TAG, content.size.toByte()) + content
	}
}
