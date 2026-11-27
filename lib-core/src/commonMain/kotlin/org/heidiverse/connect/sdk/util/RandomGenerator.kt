package org.heidiverse.connect.sdk.util

import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

@OptIn(ExperimentalEncodingApi::class)
internal object RandomGenerator {

	private val GENERATOR_BASE64 = Base64.UrlSafe.withPadding(Base64.PaddingOption.ABSENT)

	/**
	 * Generates a cryptographically secure random string of the given [bytes] length.
	 * The string is encoded in base64.
	 */
	fun generateRandomString(bytes: Int = 16, base64: Base64 = GENERATOR_BASE64): String {
		val randomBytes = secureRandomBytes(bytes)
		return base64.encode(randomBytes)
	}

}