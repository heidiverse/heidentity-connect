package org.heidiverse.connect.sdk

import org.heidiverse.connect.sdk.data.AuthTokens
import org.heidiverse.connect.sdk.data.IdpConfig
import org.heidiverse.connect.sdk.data.SavedAuthorizationFlow
import org.heidiverse.connect.sdk.dpop.DpopConfig
import org.heidiverse.connect.sdk.dpop.DpopKeyManager
import org.heidiverse.connect.sdk.dpop.DpopKeyRef
import org.heidiverse.connect.sdk.exception.StateMismatchException
import org.heidiverse.connect.sdk.exception.TokenVerificationException
import org.heidiverse.connect.sdk.networking.authorization.AuthorizationRequest
import org.heidiverse.connect.sdk.networking.configuration.ProviderMetadata
import org.heidiverse.connect.sdk.pkce.CodeChallenge
import org.heidiverse.connect.sdk.pkce.CodeVerifierGenerator
import org.heidiverse.connect.sdk.repository.AuthorizationRepository

internal class AuthorizationFlow {
	private val providerMetadata: ProviderMetadata
	private val authorizationRepository: AuthorizationRepository
	private val dpopKeyManager: DpopKeyManager
	private val codeChallenge: CodeChallenge
	private lateinit var authRequest: AuthorizationRequest

	/**
	 * The key is created here, when the authorization starts, rather than at code exchange. That is what the
	 * `dpop_jkt` authorization-code binding of RFC 9449 §10 requires. We don't support this yet, but prepare for it.
	 */
	var dpopKey: DpopKeyRef? = null
		private set

	constructor(
		codeVerifierGenerator: CodeVerifierGenerator,
		providerMetadata: ProviderMetadata,
		authorizationRepository: AuthorizationRepository,
		dpopKeyManager: DpopKeyManager,
		dpopConfig: DpopConfig?,
	) {
		this.providerMetadata = providerMetadata
		this.authorizationRepository = authorizationRepository
		this.dpopKeyManager = dpopKeyManager

		val codeVerifier = codeVerifierGenerator.generateCodeVerifier()
		this.codeChallenge = codeVerifierGenerator.deriveCodeVerifierChallenge(codeVerifier)

		// Blocking call into the secure element, see AuthorizationController.startAuthorization
		this.dpopKey = dpopConfig?.let { dpopKeyManager.createKeyForMode(it, providerMetadata) }
	}

	constructor(
		saved: SavedAuthorizationFlow,
		authorizationRepository: AuthorizationRepository,
		dpopKeyManager: DpopKeyManager,
	) {
		this.providerMetadata = saved.providerMetadata
		this.authorizationRepository = authorizationRepository
		this.dpopKeyManager = dpopKeyManager

		this.codeChallenge = saved.codeChallenge
		this.authRequest = saved.authorizationRequest
		// Never regenerate: the code was obtained for this key, and for a restored flow the key already exists
		this.dpopKey = saved.dpopKey
		// The key predates this process, so register it before an orphan sweep could mistake it for abandoned
		dpopKeyManager.trackExistingKey(saved.dpopKey)
	}

	fun createAuthorizationRequestUrl(
		clientId: String,
		redirectUri: String,
		scope: String,
		prompt: String?,
		uiLocales: String?,
		loginHint: String?,
		nativeCallbackUri: String?,
		customQueryParameters: Map<String, String>,
	): String {
		// Create the authorization request and launch it
		authRequest = AuthorizationRequest.Code(
			clientId = clientId,
			redirectUri = redirectUri,
			scope = scope,
			prompt = prompt,
			uiLocales = uiLocales,
			loginHint = loginHint,
			nativeCallbackUri = nativeCallbackUri
		)

		return authRequest.toUrl(providerMetadata.authorizationEndpoint, codeChallenge, customQueryParameters)
	}

	suspend fun exchangeAuthorizationCode(
		idpConfig: IdpConfig,
		code: String,
		state: String?,
		tokenVerificationHook: suspend (AuthTokens) -> Result<Unit>,
		customHeaders: Map<String, String> = emptyMap(),
	): Result<Unit> {
		if (state != authRequest.state) {
			// The returned state from the authorization response does not match the state sent in the authorization request
			return Result.failure(StateMismatchException())
		}

		// Exchange the authorization code for an access token
		val authTokens = authorizationRepository.exchangeAuthorizationCode(
			code = code,
			idpConfig = idpConfig,
			codeVerifier = codeChallenge.codeVerifier,
			customHeaders = customHeaders,
			dpopKeyRef = dpopKey,
		).getOrElse {
			return Result.failure(it)
		}

		authorizationRepository.setTemporaryAuthTokens(authTokens)

		// Authorization code exchange for token was successful, now check with the token verification hook if the login process is considered successful
		val tokenVerificationResult = tokenVerificationHook.invoke(authTokens)
		if (tokenVerificationResult.isSuccess) {
			authorizationRepository.setTemporaryAuthTokens(null)
			authorizationRepository.updateTokens(authTokens)
			return Result.success(Unit)
		} else {
			authorizationRepository.clearAuthTokens()
			return Result.failure(
				TokenVerificationException(tokenVerificationResult.exceptionOrNull())
			)
		}
	}

	/**
	 * Deletes the DPoP key of this flow. Must be called from every path that abandons the flow without it producing
	 * tokens, otherwise the key stays in the secure storage forever.
	 *
	 * Only reachable paths are covered by this; the ones that are not (the process being killed while the browser is
	 * open) are cleaned up by [DpopKeyManager.pruneOrphanedKeys] at the start of the next authorization.
	 */
	fun discardDpopKey() {
		dpopKeyManager.deleteKey(dpopKey)
		dpopKey = null
	}

	fun save(): SavedAuthorizationFlow =
		SavedAuthorizationFlow(
			authorizationRequest = authRequest,
			codeChallenge = codeChallenge,
			providerMetadata = providerMetadata,
			dpopKey = dpopKey,
		)
}
