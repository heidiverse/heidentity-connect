package org.heidiverse.connect.sdk.util

import java.security.MessageDigest

internal actual object Sha256 {
	actual fun digest(bytes: ByteArray): ByteArray {
		return MessageDigest.getInstance("SHA-256").digest(bytes)
	}
}
