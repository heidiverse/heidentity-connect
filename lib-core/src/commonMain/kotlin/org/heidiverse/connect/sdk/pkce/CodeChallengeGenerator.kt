package org.heidiverse.connect.sdk.pkce

internal interface CodeChallengeGenerator {

	fun generateCodeChallenge(codeVerifier: String): CodeChallenge

}