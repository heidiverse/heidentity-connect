package org.heidiverse.connect.sdk.tokenhandling

import org.heidiverse.connect.sdk.data.AuthTokens

data class AccessAndIdTokens(
	val accessToken: String,
	val idToken: String?,
)

internal fun AuthTokens.toAccessAndIdTokens() = AccessAndIdTokens(
	accessToken = accessToken,
	idToken = idToken,
)
