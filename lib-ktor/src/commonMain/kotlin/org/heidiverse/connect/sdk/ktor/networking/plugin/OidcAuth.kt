package org.heidiverse.connect.sdk.ktor.networking.plugin

import io.ktor.client.call.HttpClientCall
import io.ktor.client.plugins.api.Send
import io.ktor.client.plugins.api.createClientPlugin
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.header
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.isSuccess
import io.ktor.util.AttributeKey
import io.ktor.utils.io.InternalAPI
import org.heidiverse.connect.sdk.HeidentityConnect
import org.heidiverse.connect.sdk.dpop.DpopNonceTarget
import org.heidiverse.connect.sdk.dpop.DpopProofProvider
import org.heidiverse.connect.sdk.exception.UnauthorizedException
import org.heidiverse.connect.sdk.logging.OidcEvent
import org.heidiverse.connect.sdk.logging.OidcEventLogger
import org.heidiverse.connect.sdk.networking.AuthorizedUrlMatcher
import org.heidiverse.connect.sdk.networking.RequestType
import org.heidiverse.connect.sdk.tokenhandling.TokenHandler

private val UsedDuringTokenVerification = AttributeKey<Unit>("used-during-token-verification")
private val RequestTypeAttribute = AttributeKey<RequestType>("request-type")

/**
 * Marks a request that may be used during the token verification hook. These requests have access to a temporary auth token storage even before the login process is considered successful
 */
fun HttpRequestBuilder.usedDuringTokenVerification() {
	attributes.put(UsedDuringTokenVerification, Unit)
}

/**
 * Marks a request to always execute without sending the bearer token
 */
fun HttpRequestBuilder.alwaysUnauthorized() = requestType(RequestType.ALWAYS_UNAUTHORIZED)

/**
 * Marks a request to always execute with sending the bearer token and otherwise throw an [UnauthorizedException]
 */
fun HttpRequestBuilder.alwaysAuthorized() = requestType(RequestType.ALWAYS_AUTHORIZED)

/**
 * Marks a request to always execute, if a token is available with, and otherwise without token
 */
fun HttpRequestBuilder.opportunisticAuthorization() = requestType(RequestType.OPPORTUNISTIC)

/**
 * Marks a request with the provided request [type]. This will override the default request type set in the [OidcAuthConfig]
 */
fun HttpRequestBuilder.requestType(type: RequestType) {
	attributes.put(RequestTypeAttribute, type)
}

class OidcAuthConfig {
	/**
	 * The request type to apply if a request has not set its own request type. Defaults to [RequestType.OPPORTUNISTIC]
	 */
	var defaultRequestType: RequestType = RequestType.OPPORTUNISTIC

	/**
	 * A set of base URLs that should be called authorized.
	 *
	 * A request is only considered authorized if its scheme, host and port exactly match one of these base URLs
	 * (see [AuthorizedUrlMatcher] for the exact matching rules).
	 *
	 * A request whose URL is not authorized by this set will never be sent with the bearer token attached,
	 * regardless of its [RequestType] (see [OidcAuth] for the resulting behavior per request type).
	 */
	var authorizedBaseUrls: Set<String> = emptySet()

	/**
	 * A class providing the plugin with the necessary logic to refresh the token if it has expired and add it to the request
	 */
	var tokenHandler: TokenHandler? = null
}

/**
 * A plugin that adds the bearer token to the request headers if the user is logged in and handles token refreshes.
 * If the tokens are expired, this will automatically trigger a token refresh.
 * Additionally, if the server responds with a 401 or 403, this will also trigger a token refresh.
 * At most one token refresh is attempted per request. Whether or not the request is actually executed depends on the request type and scenario.
 *
 * There are three request types (see [RequestType] for more information):
 * 1. [RequestType.ALWAYS_UNAUTHORIZED]: These will short-circuit and skip all OIDC logic from the plugin
 * 2. [RequestType.ALWAYS_AUTHORIZED]: These will throw an [UnauthorizedException] if no valid bearer token can be sent with the request
 * 3. [RequestType.OPPORTUNISTIC]: These will always execute the request, with or without a valid bearer token
 *
 * Before any of the above applies, every request except [RequestType.ALWAYS_UNAUTHORIZED] is checked against
 * [OidcAuthConfig.authorizedBaseUrls] using [AuthorizedUrlMatcher]. If the request URL is not authorized:
 * - [RequestType.ALWAYS_AUTHORIZED] throws an [UnauthorizedException] (the bearer token is never sent to an
 *   unlisted host)
 * - [RequestType.OPPORTUNISTIC] proceeds without attaching the bearer token
 *
 * Whenever a request is attempted with this plugin applied, there are four scenarios that can happen and determine whether or not the request actually gets executed:
 * 1. No bearer token is available
 *     1. [RequestType.ALWAYS_UNAUTHORIZED] -> Executed (without token)
 *     2. [RequestType.ALWAYS_AUTHORIZED] -> Throws an [UnauthorizedException]
 *     3. [RequestType.OPPORTUNISTIC] -> Executed (without token)
 * 2. A valid bearer token is available
 *     1. [RequestType.ALWAYS_UNAUTHORIZED] -> Executed (without token)
 *     2. [RequestType.ALWAYS_AUTHORIZED] -> Executed (with token)
 *     3. [RequestType.OPPORTUNISTIC] -> Executed (with token)
 * 3. The bearer token has expired and the token refresh was successful
 *     1. [RequestType.ALWAYS_UNAUTHORIZED] -> Executed (without token)
 *     2. [RequestType.ALWAYS_AUTHORIZED] -> Executed (with token)
 *     3. [RequestType.OPPORTUNISTIC] -> Executed (with token)
 * 4. The bearer token has expired and the token refresh failed
 *     1. [RequestType.ALWAYS_UNAUTHORIZED] -> Executed (without token)
 *     2. [RequestType.ALWAYS_AUTHORIZED] -> Throws the exception that failed the token refresh or an [UnauthorizedException]
 *     3. [RequestType.OPPORTUNISTIC] -> Throws the exception that failed the token refresh or an [UnauthorizedException]
 */
@OptIn(InternalAPI::class)
val OidcAuth = createClientPlugin("OidcAuth", ::OidcAuthConfig) {

	val logger: OidcEventLogger = HeidentityConnect.getEventLogger()
	val dpopProofProvider: DpopProofProvider = HeidentityConnect.getDpopProofProvider()

	/**
	 * Executes the request with the access token in the authorization header, refreshing it if necessary.
	 * Uses Bearer or DPoP depending on the token type.
	 * If the request fails with a 401 or 403, it will attempt to refresh the token and retry the request once.
	 */
	suspend fun Send.Sender.executeRequestWithAccessToken(
		requestType: RequestType,
		tokenHandler: TokenHandler,
		originalRequest: HttpRequestBuilder,
		forceTokenRefresh: Boolean = false,
		dpopRetried: Boolean = false,
	): HttpClientCall {
		// Check if this request may be called during login verification and therefore has access to the temporary auth tokens
		val usedDuringTokenVerification = originalRequest.attributes.contains(UsedDuringTokenVerification)

		if (tokenHandler.isAuthorized(usedDuringTokenVerification)) {
			val requestUrl = originalRequest.url.toString()

			// Copy the original request in order to modify it with the bearer token
			val authorizedRequest = HttpRequestBuilder().takeFromWithExecutionContext(originalRequest)

			// Remove previous / old Authorization header, and any proof left over from an earlier attempt — a proof is
			// bound to one method, URI and nonce and must never be reused
			authorizedRequest.headers.remove(HttpHeaders.Authorization)
			authorizedRequest.headers.remove(DpopProofProvider.HEADER_DPOP)

			val tokenResult = tokenHandler.getBearerToken(usedDuringTokenVerification, forceTokenRefresh)
			if (tokenResult.bearerToken.isSuccess) {
				// A valid (and possibly refreshed) bearer token is available, add it to the Authorization header
				val bearerToken = tokenResult.bearerToken.getOrThrow()
				val dpopKeyRef = tokenResult.dpopKeyRef

				if (dpopKeyRef != null) {
					// The token is sender-constrained, replace the Bearer with DPoP scheme and add a proof that binds this
					// exact request and token.
					authorizedRequest.headers.append(
						HttpHeaders.Authorization,
						"${DpopProofProvider.AUTHORIZATION_SCHEME_DPOP} $bearerToken",
					)
					authorizedRequest.header(
						DpopProofProvider.HEADER_DPOP,
						dpopProofProvider.createProof(
							keyRef = dpopKeyRef,
							httpMethod = authorizedRequest.method.value,
							url = requestUrl,
							target = DpopNonceTarget.RESOURCE_SERVER,
							accessToken = bearerToken,
						),
					)
				} else {
					authorizedRequest.bearerAuth(bearerToken)
				}
			} else {
				if (tokenResult.didTokenRefresh) {
					// No valid bearer token is available and we did a token refresh. Throw the exception that failed the token
					// refresh or an UnauthorizedException
					// Note: Opportunistic requests will also throw upon a failed token refresh, even if they work without a token.
					// This is done to prevent a token refresh failure from returning wrong data
					throw tokenResult.bearerToken.exceptionOrNull() ?: UnauthorizedException()
				} else {
					// No valid bearer token and we didn't do a token refresh. This is the highly unlikely case that the token
					// expired between the time we checked if a token refresh is needed and the time we tried to use it
					return executeRequestWithAccessToken(
						requestType,
						tokenHandler,
						originalRequest,
						forceTokenRefresh = true,
						dpopRetried = dpopRetried,
					)
				}
			}

			// Execute the request and check its response
			val call = proceed(authorizedRequest)
			val responseStatus = call.response.status

			// Pick up a rotated nonce and the server time on any response, and detect a DPoP rejection. Checked before the
			// 401 handling below because a rejected proof is not a stale token and must not consume the token refresh.
			if (tokenResult.dpopKeyRef != null) {
				val needsDpopRetry = dpopProofProvider.handleResponse(
					target = DpopNonceTarget.RESOURCE_SERVER,
					url = requestUrl,
					statusCode = responseStatus.value,
					headerValue = { call.response.headers[it] },
				)
				if (needsDpopRetry && !dpopRetried) {
					return executeRequestWithAccessToken(
						requestType,
						tokenHandler,
						originalRequest,
						forceTokenRefresh = forceTokenRefresh,
						dpopRetried = true,
					)
				}
			}

			when {
				responseStatus.isSuccess() -> {
					// Bearer token was accepted by the server
					return call
				}

				responseStatus in setOf(HttpStatusCode.Unauthorized, HttpStatusCode.Forbidden) -> {
					logger.logEvent(OidcEvent.UnauthorizedRequest(requestUrl, responseStatus.value, tokenResult.didTokenRefresh))

					return if (tokenResult.didTokenRefresh) {
						// The tokens were already refreshed once but the server still responded with a 401 or 403, so there must be another issue
						call
					} else {
						// Server returned a 401 or 403 but we didn't refresh the tokens yet, so try again with a forced token refresh
						executeRequestWithAccessToken(
							requestType,
							tokenHandler,
							originalRequest,
							forceTokenRefresh = true,
							dpopRetried = dpopRetried,
						)
					}
				}

				else -> {
					// Server returned an error code other than a 401 or 403
					return call
				}
			}
		} else {
			// User is not logged in
			if (requestType == RequestType.ALWAYS_AUTHORIZED) {
				// The request requires to be authorized, throw an exception
				throw UnauthorizedException()
			} else {
				// The request is opportunistic, so proceed without any OIDC logic
				return proceed(originalRequest)
			}
		}
	}

	on(Send) { originalRequest ->
		val requestType = originalRequest.attributes.getOrNull(RequestTypeAttribute) ?: pluginConfig.defaultRequestType

		// Proceed the chain without modifications if the request was should always execute unauthorized
		if (requestType == RequestType.ALWAYS_UNAUTHORIZED) {
			return@on proceed(originalRequest)
		}

		// If the request url doesn't match any of the authorized base urls, never send the bearer token to it.
		// Opportunistic requests proceed without a token, while requests that require authorization are rejected.
		val requestUrl = originalRequest.url.toString()
		if (!AuthorizedUrlMatcher.matches(requestUrl, pluginConfig.authorizedBaseUrls)) {
			if (requestType == RequestType.ALWAYS_AUTHORIZED) {
				throw UnauthorizedException()
			}
			return@on proceed(originalRequest)
		}

		// Execute the request with the access token in the authorization header
		val tokenHandler = pluginConfig.tokenHandler ?: return@on proceed(originalRequest)
		val call = executeRequestWithAccessToken(requestType, tokenHandler, originalRequest)
		return@on call
	}
}