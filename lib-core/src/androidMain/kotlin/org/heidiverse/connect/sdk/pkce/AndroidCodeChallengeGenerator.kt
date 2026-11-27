package org.heidiverse.connect.sdk.pkce

import java.security.MessageDigest
import kotlin.io.encoding.ExperimentalEncodingApi

internal class AndroidCodeChallengeGenerator : CodeChallengeGenerator {

	@OptIn(ExperimentalEncodingApi::class)
	override fun generateCodeChallenge(codeVerifier: String): CodeChallenge {
		val digest = MessageDigest.getInstance("SHA-256")
		digest.update(codeVerifier.encodeToByteArray())
		val digestBytes = digest.digest()
		val codeChallenge = CodeVerifierGenerator.PKCE_BASE64.encode(digestBytes)
		return CodeChallenge(
			codeVerifier = codeVerifier,
			codeChallenge = codeChallenge,
			codeChallengeMethod = CodeChallengeMethod.S256
		)
	}

}