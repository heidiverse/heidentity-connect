package org.heidiverse.connect.sdk.networking

enum class RequestType {
	/**
	 * Requests that should never send a token. These will skip the entire OIDC logic
	 */
	ALWAYS_UNAUTHORIZED,

	/**
	 * Requests that should always send a token. These will throw an UnauthorizedException if the user is not logged in
	 */
	ALWAYS_AUTHORIZED,

	/**
	 * Requests that work with or without a token. These will send a token if available and always execute the request
	 */
	OPPORTUNISTIC,
}