package org.heidiverse.connect.sdk.tokenhandling

import org.heidiverse.connect.sdk.dpop.DpopKeyRef

data class TokenResult(
	/** Indicates whether the [bearerToken] result is a refreshed token or not */
	val didTokenRefresh: Boolean,

	/** The result of the bearer token retrieval */
	val bearerToken: Result<String>,

	/**
	 * Set only when the access token in [bearerToken] is DPoP-bound, i.e. when it must be sent with an
	 * `Authorization: DPoP` header plus a proof signed with this key rather than as a plain bearer token.
	 *
	 * Null whenever the token is a plain bearer token.
	 */
	val dpopKeyRef: DpopKeyRef? = null,
)
