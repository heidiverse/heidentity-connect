package org.heidiverse.connect.sdk.tokenhandling

data class AccessAndIdTokensResult(
	/** Indicates whether the [tokens] result contains refreshed tokens */
	val didTokenRefresh: Boolean,

	/** The result of the token retrieval */
	val tokens: Result<AccessAndIdTokens>,
)
