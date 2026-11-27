package org.heidiverse.connect.sdk.okhttp.networking.interceptor

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import okhttp3.Interceptor
import okhttp3.Request
import okhttp3.Response
import org.heidiverse.connect.sdk.di.HeidentityConnectKoinComponent
import org.heidiverse.connect.sdk.dpop.DpopNonceTarget
import org.heidiverse.connect.sdk.dpop.DpopProofProvider
import org.heidiverse.connect.sdk.exception.UnauthorizedException
import org.heidiverse.connect.sdk.networking.AuthorizedUrlMatcher
import org.heidiverse.connect.sdk.tokenhandling.TokenHandler
import org.koin.core.component.inject
import java.io.IOException

/**
 * Interceptor that takes care of the OAuth token handling:
 * - Performs every request with a fresh access token
 * - Adds the access token as the Authorization Header
 *
 *
TODO: is this needed?
 * - Notifies the [authStateHandler] about a session expiration when the IDP responds with an INVALID_GRANT error
 *
 *
 * - Force refreshes the access token and retries the request once if the server responds with a 401 or 403
 * - Ensures only one token refresh is happening at once and waits for it to finish when multiple requests are happening at the same time
 * - Sends DPoP-bound access tokens under the `DPoP` scheme with a per-request proof, and retries once when the server
 *   rejects the proof with a nonce challenge or because of the device clock
 */
class AuthorizationHeaderInterceptor(
	private val tokenHandler: TokenHandler,
	private val authorizedBaseUrls: Set<String> = emptySet(),
	private val dispatcher: CoroutineDispatcher = Dispatchers.IO,
	private val throwOnUnauthorized: Boolean = true,
) : Interceptor, HeidentityConnectKoinComponent {

	/** Resolved on first use, since an interceptor can be constructed before the SDK is initialized. */
	private val dpopProofProvider by inject<DpopProofProvider>()

	companion object {
		private const val HEADER_AUTHORIZATION = "Authorization"
		private const val BEARER_TOKEN_PREFIX = "Bearer"

		private const val HTTP_UNAUTHORIZED = 401
		private const val HTTP_FORBIDDEN = 403
	}

	override fun intercept(chain: Interceptor.Chain): Response {
		val request = chain.request()

		// Proceed the chain without modifications if the request was annotated as always unauthorized
		/*val requestInvocation = request.tag(Invocation::class.java)
		val requestAlwaysUnauthorized = requestInvocation?.method()?.getAnnotation(AlwaysUnauthorized::class.java) != null
		if (requestAlwaysUnauthorized) {
		   return chain.proceed(request)
		}*/

		// If the request url doesn't match any of the authorized base urls, proceed without sending the bearer token
		val requestUrl = request.url.toString()
		if (!AuthorizedUrlMatcher.matches(requestUrl, authorizedBaseUrls)) {
			return chain.proceed(request)
		}

		val response = runBlocking(dispatcher) {
			executeRequestWithFreshToken(chain, tokenHandler, request)
		}

		return response
	}

	/**
	 * @param dpopRetried tracked separately from [forceTokenRefresh] so that a DPoP rejection does not consume the
	 *   single token refresh a request is allowed.
	 */
	private suspend fun executeRequestWithFreshToken(
		chain: Interceptor.Chain,
		tokenHandler: TokenHandler,
		request: Request,
		forceTokenRefresh: Boolean = false,
		dpopRetried: Boolean = false,
	): Response {
		if (tokenHandler.isAuthorized(false)) {
			try {

				val tokenResult = tokenHandler.getBearerToken(false, forceTokenRefresh)

				// Copy the original request in order to modify it with the bearer token
				val authorizedRequestBuilder = request.newBuilder()
				// Remove previous / old Authorization header, and any proof left over from an earlier attempt — a proof is
				// bound to one method, URI and nonce and must never be reused
				authorizedRequestBuilder.removeHeader(HEADER_AUTHORIZATION)
				authorizedRequestBuilder.removeHeader(DpopProofProvider.HEADER_DPOP)

				if (tokenResult.bearerToken.isSuccess) {
					// A valid (and possibly refreshed) bearer token is available, add it to the Authorization header
					val bearerToken = tokenResult.bearerToken.getOrThrow()
					val dpopKeyRef = tokenResult.dpopKeyRef

					if (dpopKeyRef != null) {
						// The token is sender-constrained, so it goes out under the DPoP scheme with a proof that binds this
						// exact request and token. Minted here rather than inside getBearerToken so that the secure element
						// is never used while the token refresh lock is held.
						authorizedRequestBuilder.addHeader(
							HEADER_AUTHORIZATION,
							"${DpopProofProvider.AUTHORIZATION_SCHEME_DPOP} $bearerToken",
						)
						authorizedRequestBuilder.addHeader(
							DpopProofProvider.HEADER_DPOP,
							dpopProofProvider.createProof(
								keyRef = dpopKeyRef,
								httpMethod = request.method,
								url = request.url.toString(),
								target = DpopNonceTarget.RESOURCE_SERVER,
								// Hash the token that is actually sent: bearerTokenProvider is caller-configurable
								accessToken = bearerToken,
							),
						)
					} else {
						authorizedRequestBuilder.addHeader(HEADER_AUTHORIZATION, "$BEARER_TOKEN_PREFIX $bearerToken")
					}
				} else {
					if (tokenResult.didTokenRefresh) {
						// No valid bearer token is available and we did a token refresh. Throw the exception that failed the token
						// refresh or an UnauthorizedException
						throw tokenResult.bearerToken.exceptionOrNull() ?: UnauthorizedException()
					} else {
						// No valid bearer token and we didn't do a token refresh. This is the highly unlikely case that the token
						// expired between the time we checked if a token refresh is needed and the time we tried to use it
						return executeRequestWithFreshToken(
							chain,
							tokenHandler,
							request,
							forceTokenRefresh = true,
							dpopRetried = dpopRetried,
						)
					}
				}

				// Execute the request and check its response
				val response = chain.proceed(authorizedRequestBuilder.build())

				// Pick up a rotated nonce and the server time on any response, and detect a DPoP rejection. Checked before the
				// 401 handling below because a rejected proof is not a stale token and must not consume the token refresh.
				if (tokenResult.dpopKeyRef != null) {
					val needsDpopRetry = dpopProofProvider.handleResponse(
						target = DpopNonceTarget.RESOURCE_SERVER,
						url = request.url.toString(),
						statusCode = response.code,
						headerValue = { response.header(it) },
					)
					if (needsDpopRetry && !dpopRetried && request.body?.isOneShot() != true) {
						response.close()
						return executeRequestWithFreshToken(
							chain,
							tokenHandler,
							request,
							forceTokenRefresh = forceTokenRefresh,
							dpopRetried = true,
						)
					}
				}

				when {
					response.isSuccessful -> {
						// Bearer token was accepted by the server
						return response
					}

					response.code in setOf(HTTP_UNAUTHORIZED, HTTP_FORBIDDEN) -> {
						return if (tokenResult.didTokenRefresh) {
							// The tokens were already refreshed once but the server still responded with a 401 or 403, so there must be another issue
							response
						} else {
							// Server returned a 401 or 403 but we didn't refresh the tokens yet, so try again with a forced token refresh
							response.close()
							executeRequestWithFreshToken(
								chain,
								tokenHandler,
								request,
								forceTokenRefresh = true,
								dpopRetried = dpopRetried,
							)
						}
					}

					else -> {
						// Server returned an error code other than a 401 or 403
						return response
					}
				}
			} catch (e: CancellationException) {
				throw e
			} catch (e: IOException) {
				throw e
			} catch (e: Exception) {
				// Non-IOExceptions in interceptors crash the app instead of reporting it to the caller (https://github.com/square/okhttp/issues/5151)
				throw IOException(e)
			}
		} else {
			// If the user is not logged in, either continue with an UnauthorizedException or normally execute the request and let the caller handle any 401/403 responses
			if (throwOnUnauthorized) {
				// Wrapped for the same reason as above: a non-IOException would crash the app on asynchronous calls
				throw IOException(UnauthorizedException())
			} else {
				return chain.proceed(request)
			}
		}
	}

}
