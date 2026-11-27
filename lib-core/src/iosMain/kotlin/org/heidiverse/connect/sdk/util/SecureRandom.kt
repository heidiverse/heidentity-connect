package org.heidiverse.connect.sdk.util

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.convert
import kotlinx.cinterop.usePinned
import platform.Security.SecRandomCopyBytes
import platform.Security.errSecSuccess
import platform.Security.kSecRandomDefault

@OptIn(ExperimentalForeignApi::class)
internal actual fun secureRandomBytes(size: Int): ByteArray {
	if (size == 0) {
		return ByteArray(0)
	}

	val bytes = ByteArray(size)
	val status = bytes.usePinned { pinned ->
		SecRandomCopyBytes(kSecRandomDefault, size.convert(), pinned.addressOf(0))
	}
	check(status == errSecSuccess) {
		"SecRandomCopyBytes failed with status $status"
	}
	return bytes
}
