package org.heidiverse.connect.sdk.pkce

import kotlinx.serialization.Serializable

@Serializable
data class CodeChallenge(
	val codeVerifier: String,
	val codeChallenge: String,
	val codeChallengeMethod: CodeChallengeMethod,
)
