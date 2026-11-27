package org.heidiverse.connect.sdk.dpop

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.heidiverse.connect.sdk.logging.OidcEvent
import org.heidiverse.connect.sdk.logging.OidcEventLogger

/**
 * The single entry point the HTTP layers use for [DPoP (RFC 9449)](https://datatracker.ietf.org/doc/html/rfc9449).
 *
 * Everything protocol-related lives here so that the four call sites that need it (the Ktor and OkHttp token services,
 * the Ktor plugin and the OkHttp interceptor) only contribute their own transport plumbing — build request, send, retry
 * once — and never their own copy of the rules.
 */
class DpopProofProvider internal constructor(
	private val proofFactory: DpopProofFactory,
	private val nonceStore: DpopNonceStore,
	private val clock: DpopClock,
	private val logger: OidcEventLogger,
	/**
	 * Called when a proof cannot be created because the key is gone. The tokens bound to that key are unusable from then
	 * on, so the session is expired right away rather than on the next token refresh.
	 */
	private val onKeyMissing: (DpopKeyRef) -> Unit = {},
) {

	companion object {
		const val HEADER_DPOP = "DPoP"

		const val HEADER_DPOP_NONCE = "DPoP-Nonce"

		const val AUTHORIZATION_SCHEME_DPOP = "DPoP"

		/** The error code a server uses to demand a nonce (RFC 9449 §8/§9). */
		const val ERROR_USE_DPOP_NONCE = "use_dpop_nonce"

		/** The error code a server uses to reject a proof (RFC 9449 §7.1), e.g. because its `iat` is out of range. */
		const val ERROR_INVALID_DPOP_PROOF = "invalid_dpop_proof"

		private const val HEADER_WWW_AUTHENTICATE = "WWW-Authenticate"
		private const val HEADER_DATE = "Date"
	}

	/**
	 * Creates a proof for a request, including the most recent nonce for [target] if one is cached.
	 *
	 * Signing goes through the secure element and can take a noticeable amount of time. For a *resource* request, mint the
	 * proof after the access token has been obtained rather than while obtaining it: the token handler holds a mutex for
	 * the duration, and signing inside it would serialize every concurrent request behind the secure element.
	 *
	 * @param accessToken the token that will be sent in the `Authorization` header, which adds the `ath` claim binding
	 *   the proof to it. Must be null at the token endpoint, where no access token is presented.
	 * @throws DpopException if the key is gone or the signature fails.
	 */
	suspend fun createProof(
		keyRef: DpopKeyRef,
		httpMethod: String,
		url: String,
		target: DpopNonceTarget,
		accessToken: String? = null,
	): String {
		val nonce = nonceStore.get(target, url)
		return try {
			withContext(Dispatchers.Default) {
				proofFactory.createProof(
					keyRef = keyRef,
					httpMethod = httpMethod,
					url = url,
					accessToken = accessToken,
					nonce = nonce,
				)
			}
		} catch (e: DpopException) {
			logger.logEvent(OidcEvent.DpopProofCreationFailed(e))
			if (e.reason == DpopException.Reason.KEY_MISSING) {
				onKeyMissing(keyRef)
			}
			throw e
		}
	}

	/**
	 * Records what the server told us about DPoP and reports whether the request should be re-sent with a freshly minted
	 * proof. Call this for *every* response of a DPoP request, not just failures: a server may rotate the nonce on a
	 * successful response too (RFC 9449 §8).
	 *
	 * A retry is requested for two kinds of rejection:
	 * - a nonce challenge (`use_dpop_nonce`) that came with a new `DPoP-Nonce`
	 * - a rejected proof (`invalid_dpop_proof`) that came with a `Date` header. The most likely cause is a wrong device
	 *   clock, so the server's time is adopted for all following proofs.
	 *
	 * The caller owns the retry budget — this must only be acted on once per request, tracked with a flag that is
	 * separate from the token-refresh retry so that a DPoP rejection does not consume it.
	 *
	 * @param headerValue reads a response header by name, case-insensitively
	 * @param body the response body, if already available. Only used for the authorization-server flavour of the
	 *   rejection, where the error code is in the body.
	 * @return true if the request should be re-sent with a freshly minted proof
	 */
	suspend fun handleResponse(
		target: DpopNonceTarget,
		url: String,
		statusCode: Int,
		headerValue: (String) -> String?,
		body: String? = null,
	): Boolean {
		val nonce = headerValue(HEADER_DPOP_NONCE)
		if (nonce != null) {
			nonceStore.store(target, url, nonce)
		}

		val isNonceChallenge = nonce != null && hasError(target, statusCode, headerValue, body, ERROR_USE_DPOP_NONCE)
		if (isNonceChallenge) {
			logger.logEvent(OidcEvent.DpopNonceRequired(target.name, url))
		}

		val isClockSkewRejection = hasError(target, statusCode, headerValue, body, ERROR_INVALID_DPOP_PROOF) &&
			clock.syncTo(headerValue(HEADER_DATE))
		if (isClockSkewRejection) {
			logger.logEvent(OidcEvent.DpopClockSkewCorrected(clock.offsetSeconds))
		}

		return isNonceChallenge || isClockSkewRejection
	}

	private fun hasError(
		target: DpopNonceTarget,
		statusCode: Int,
		headerValue: (String) -> String?,
		body: String?,
		errorCode: String,
	): Boolean = when (target) {
		// RFC 9449 §8: 400 with the error code in the token error response
		DpopNonceTarget.AUTHORIZATION_SERVER ->
			statusCode == 400 && body?.contains(errorCode, ignoreCase = true) == true

		// RFC 9449 §9: 401 with a DPoP WWW-Authenticate challenge carrying the error code
		DpopNonceTarget.RESOURCE_SERVER ->
			statusCode == 401 && headerValue(HEADER_WWW_AUTHENTICATE)?.contains(errorCode, ignoreCase = true) == true
	}
}
