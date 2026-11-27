package org.heidiverse.connect.sdk.util

import java.security.SecureRandom

/**
 * A single shared [SecureRandom] instance used to generate the random bytes on Android.
 */
private val secureRandom = SecureRandom()

internal actual fun secureRandomBytes(size: Int): ByteArray {
	val bytes = ByteArray(size)
	secureRandom.nextBytes(bytes)
	return bytes
}
