package org.heidiverse.connect.sdk.pkce

import kotlinx.serialization.Serializable

@Serializable
enum class CodeChallengeMethod(val identifier: String) {
	PLAIN("plain"),
	S256("S256"),
}