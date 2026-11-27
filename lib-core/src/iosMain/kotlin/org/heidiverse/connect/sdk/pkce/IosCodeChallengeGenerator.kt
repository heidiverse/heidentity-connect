package org.heidiverse.connect.sdk.pkce

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.convert
import kotlinx.cinterop.usePinned
import platform.CoreCrypto.CC_SHA256
import platform.CoreCrypto.CC_SHA256_DIGEST_LENGTH
import kotlin.io.encoding.ExperimentalEncodingApi

internal class IosCodeChallengeGenerator : CodeChallengeGenerator {

	@OptIn(ExperimentalEncodingApi::class, ExperimentalForeignApi::class)
	override fun generateCodeChallenge(codeVerifier: String): CodeChallenge {
		val codeVerifierBytes = codeVerifier.encodeToByteArray()
		val digest = UByteArray(CC_SHA256_DIGEST_LENGTH)

		codeVerifierBytes.usePinned { inputPinned ->
			digest.usePinned { digestPinned ->
				CC_SHA256(inputPinned.addressOf(0), codeVerifierBytes.size.convert(), digestPinned.addressOf(0))
			}
		}

		val codeChallenge = CodeVerifierGenerator.PKCE_BASE64.encode(digest.toByteArray())
		return CodeChallenge(
			codeVerifier = codeVerifier,
			codeChallenge = codeChallenge,
			codeChallengeMethod = CodeChallengeMethod.S256
		)
	}
}