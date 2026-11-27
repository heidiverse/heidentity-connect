package org.heidiverse.connect.sdk.dpop

/**
 * Conversion of ECDSA signatures from the ASN.1/DER form that both platform crypto APIs produce
 * (`SEQUENCE { INTEGER r, INTEGER s }`) to the fixed-width `R || S` concatenation that JOSE requires
 * ([RFC 7515 Appendix A.3](https://datatracker.ietf.org/doc/html/rfc7515#appendix-A.3)).
 *
 * Sending the DER form instead is the classic ES256 mistake: it is accepted by nothing and produces an opaque
 * "invalid signature" from the server.
 */
internal object EcdsaSignature {

	/**
	 * @param fieldSize the size in bytes of one coordinate for the curve (32 for P-256)
	 * @throws IllegalArgumentException if [der] is not a well-formed ECDSA signature
	 */
	fun derToJose(der: ByteArray, fieldSize: Int): ByteArray {
		val reader = DerReader(der)

		require(reader.readByte() == SEQUENCE_TAG) { "Expected a DER SEQUENCE" }
		val sequenceLength = reader.readLength()
		require(sequenceLength == reader.remaining) { "DER SEQUENCE length does not match the signature length" }

		val r = reader.readInteger()
		val s = reader.readInteger()
		require(reader.remaining == 0) { "Unexpected trailing data in the DER signature" }

		return ByteArray(fieldSize * 2).also { jose ->
			r.copyIntoFixedWidth(jose, 0, fieldSize)
			s.copyIntoFixedWidth(jose, fieldSize, fieldSize)
		}
	}

	/**
	 * Writes this unsigned big-endian integer into [destination] right-aligned in a [width]-byte window, i.e. padded
	 * with leading zeros. DER integers are signed, so they carry a leading 0x00 whenever the high bit is set and omit
	 * leading zeros otherwise — neither of which JOSE wants.
	 */
	private fun ByteArray.copyIntoFixedWidth(destination: ByteArray, offset: Int, width: Int) {
		val stripped = dropWhile { it == 0.toByte() }.toByteArray()
		require(stripped.size <= width) { "Signature component is ${stripped.size} bytes, expected at most $width" }
		stripped.copyInto(destination, offset + width - stripped.size)
	}

	private const val SEQUENCE_TAG: Byte = 0x30
	private const val INTEGER_TAG: Byte = 0x02

	private class DerReader(private val bytes: ByteArray) {
		private var index = 0

		val remaining: Int get() = bytes.size - index

		fun readByte(): Byte {
			require(index < bytes.size) { "Truncated DER signature" }
			return bytes[index++]
		}

		fun readLength(): Int {
			val first = readByte().toInt() and 0xff
			if (first and 0x80 == 0) return first

			val lengthBytes = first and 0x7f
			require(lengthBytes in 1..4) { "Unsupported DER length of $lengthBytes bytes" }
			var length = 0
			repeat(lengthBytes) {
				length = (length shl 8) or (readByte().toInt() and 0xff)
			}
			return length
		}

		fun readInteger(): ByteArray {
			require(readByte() == INTEGER_TAG) { "Expected a DER INTEGER" }
			val length = readLength()
			require(length in 1..remaining) { "Invalid DER INTEGER length of $length" }
			return bytes.copyOfRange(index, index + length).also { index += length }
		}
	}
}
