package org.heidiverse.connect.sdk.util

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.convert
import kotlinx.cinterop.usePinned
import platform.CoreCrypto.CC_SHA256
import platform.CoreCrypto.CC_SHA256_DIGEST_LENGTH

internal actual object Sha256 {

	@OptIn(ExperimentalForeignApi::class)
	actual fun digest(bytes: ByteArray): ByteArray {
		val digest = UByteArray(CC_SHA256_DIGEST_LENGTH)

		// CC_SHA256 rejects a null input pointer, which is what addressOf(0) returns for an empty array
		if (bytes.isEmpty()) {
			digest.usePinned { digestPinned ->
				CC_SHA256(null, 0.convert(), digestPinned.addressOf(0))
			}
		} else {
			bytes.usePinned { inputPinned ->
				digest.usePinned { digestPinned ->
					CC_SHA256(inputPinned.addressOf(0), bytes.size.convert(), digestPinned.addressOf(0))
				}
			}
		}

		return digest.toByteArray()
	}
}
