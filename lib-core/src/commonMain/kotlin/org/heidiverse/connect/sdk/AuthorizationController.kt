package org.heidiverse.connect.sdk

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.heidiverse.connect.sdk.browser.AuthorizationRequestLauncher
import org.heidiverse.connect.sdk.data.AuthTokens
import org.heidiverse.connect.sdk.data.IdpConfig
import org.heidiverse.connect.sdk.data.SavedAuthorizationFlow
import org.heidiverse.connect.sdk.di.HeidentityConnectKoinComponent
import org.heidiverse.connect.sdk.dpop.DpopKeyManager
import org.heidiverse.connect.sdk.exception.*
import org.heidiverse.connect.sdk.networking.authorization.AuthorizationResponse
import org.heidiverse.connect.sdk.networking.configuration.ProviderMetadata
import org.heidiverse.connect.sdk.pkce.CodeVerifierGenerator
import org.heidiverse.connect.sdk.repository.AuthorizationRepository
import org.koin.core.component.inject

class AuthorizationController(
	private var idpConfig: IdpConfig,
	savedAuthorizationFlow: SavedAuthorizationFlow? = null,
) : HeidentityConnectKoinComponent {

	private val authRepository by inject<AuthorizationRepository>()
	private val codeVerifierGenerator by inject<CodeVerifierGenerator>()
	private val dpopKeyManager by inject<DpopKeyManager>()

	private var authorizationFlow: AuthorizationFlow? = null

	init {
		savedAuthorizationFlow?.let {
			authorizationFlow = AuthorizationFlow(it, authRepository, dpopKeyManager)
		}
	}

	fun getIdpConfig(): IdpConfig = idpConfig

	/**
	 * Update the IDP configuration used by the controller. This will clear the current authorization state and provider metadata
	 */
	fun updateIdpConfig(idpConfig: IdpConfig) {
		clearAuthState(keepMetadata = false)
		// The new provider needs its own key, so any flow started against the previous one is abandoned here
		discardAuthorizationFlow()
		this.idpConfig = idpConfig
	}

	private var isAuthorizationInProgress = false

	/**
	 * Checks the current authorization state
	 */
	fun isAuthorized() = authRepository.isAuthorized()

	/**
	 * Flow emitting changes in the authorization state. Will always emit at least once for the current state
	 */
	fun getAuthorizedState() = authRepository.authorizedState

	/**
	 * Get the current auth tokens, or null if the user is not authorized
	 */
	fun getAuthTokens() = authRepository.getAuthTokens()

	/**
	 * Flow emitting changes to the current auth tokens (null when logged out)
	 */
	fun getAuthTokenFlow(): Flow<AuthTokens?> = authRepository.getAuthTokenFlow()

	/**
	 * Flow emitting session expiration events. May never emit, if the session is always refreshed without issues
	 */
	fun getSessionExpiration() = authRepository.sessionExpired.map { expired ->
		expired.takeIf { isAuthorizationInProgress.not() }
	}.filterNotNull()

	fun saveAuthorizationFlow(): SavedAuthorizationFlow =
		requireAuthorizationFlow().save()

	/**
	 * Discover the IDP configuration from its discovery endpoint using the [idpConfig] provided in the constructor
	 *
	 * @param forceFetch If set to true, the metadata will be fetched even if it is already present in the repository
	 * @param customHeaders Custom headers sent in the request
	 */
	suspend fun discoverProvider(
		forceFetch: Boolean = false,
		customHeaders: Map<String, String> = emptyMap(),
	): Result<ProviderMetadata?> {
		return runCatching { fetchMetadata(forceFetch, customHeaders) }
	}

	/**
	 * Starts a new authorization flow
	 *
	 * @param authRequestLauncher An instance of [AuthorizationRequestLauncher] to launch the authorization request
	 * @param scope The OIDC scope to request from the IDP
	 * @param prompt The OIDC prompt parameter to use for the authorization request
	 * @param uiLocales The OIDC ui_locales parameter to use for the authorization request
	 * @param loginHint The OIDC login_hint parameter to use for the authorization request
	 * @param customQueryParameters A key-value map of query parameters that gets appended to the authorization url
	 * @param customHeaders A key-value map of custom headers that get passed to the [authRequestLauncher]
	 */
	suspend fun startAuthorization(
		authRequestLauncher: AuthorizationRequestLauncher,
		scope: String,
		prompt: String? = null,
		uiLocales: String? = null,
		loginHint: String? = null,
		customQueryParameters: Map<String, String> = emptyMap(),
		customHeaders: Map<String, String> = emptyMap(),
	): Result<Unit> {
		isAuthorizationInProgress = true
		// Get the provider metadata to read the authorization endpoint from
		val metadata = runCatching {
			authRepository.getProviderMetadata()
				?: fetchMetadata(customHeaders = customHeaders)
				?: throw MetadataException(
					errorType = AuthorizationErrorType.AUTHORIZATION,
					reason = MetadataException.Reason.NOT_AVAILABLE,
					message = "Provider metadata not available",
				)
		}.getOrElse { return Result.failure(it) }

		// Key deletion, key enumeration and especially key generation are blocking calls into the secure element, which
		// can take well over a second with StrongBox. startAuthorization is typically called from the main thread.
		authorizationFlow = try {
			withContext(Dispatchers.IO) {
				// Starting a new flow abandons any previous one, so release its DPoP key before losing the reference to it
				discardAuthorizationFlow()

				// Clean up keys left behind by flows this controller can no longer see
				authRepository.pruneOrphanedDpopKeys(emptyList())

				AuthorizationFlow(codeVerifierGenerator, metadata, authRepository, dpopKeyManager, idpConfig.dpop)
			}
		} catch (e: CancellationException) {
			isAuthorizationInProgress = false
			throw e
		} catch (e: Exception) {
			// DPoP key generation can fail, e.g. when no secure element is available and the software fallback is off.
			// This surfaces before the browser is opened.
			isAuthorizationInProgress = false
			return Result.failure(e)
		}

		val authRequestUrl = runCatching {
			requireAuthorizationFlow().createAuthorizationRequestUrl(
				clientId = idpConfig.clientId,
				redirectUri = idpConfig.redirectUri,
				scope = scope,
				prompt = prompt,
				uiLocales = uiLocales,
				loginHint = loginHint,
				nativeCallbackUri = idpConfig.nativeCallbackUri,
				customQueryParameters = customQueryParameters,
			)
		}.getOrElse { return Result.failure(it) }

		return runCatching { authRequestLauncher.launchAuthorizationRequest(authRequestUrl, idpConfig, customHeaders) }
	}

	/**
	 * Handles an authorization response from the authorization request
	 *
	 * @param authResponse The response from the authorization request
	 * @param tokenVerificationHook A lambda to decide whether or not the login process should be considered successful after the token exchange.
	 * 		This can be used e.g. to do a UserInfo request to see if the tokens are actually valid
	 */
	suspend fun handleAuthorizationResponse(
		authResponse: AuthorizationResponse,
		tokenVerificationHook: suspend (AuthTokens) -> Result<Unit> = { Result.success(Unit) },
		customHeaders: Map<String, String> = emptyMap(),
	): Result<Unit> {
		when (authResponse) {
			is AuthorizationResponse.Success -> {
				val result = runCatching {
					requireAuthorizationFlow()
						.exchangeAuthorizationCode(
							idpConfig,
							authResponse.code,
							authResponse.state,
							tokenVerificationHook,
							customHeaders
						)
						.getOrThrow()
				}
				if (result.isSuccess) {
					// The tokens now own the key, so the flow must not delete it
					authorizationFlow = null
				} else {
					// A failed exchange or a rejected token verification hook leaves no tokens holding the key
					discardAuthorizationFlow()
				}
				isAuthorizationInProgress = false
				return result
			}

			is AuthorizationResponse.Error -> {
				discardAuthorizationFlow()
				isAuthorizationInProgress = false
				val exception = AuthorizationException(authResponse.errorType, authResponse.message)
				return Result.failure(exception)
			}

			is AuthorizationResponse.BrowserNotFoundError -> {
				discardAuthorizationFlow()
				isAuthorizationInProgress = false
				val exception = BrowserNotFoundException()
				return Result.failure(exception)
			}

			is AuthorizationResponse.MalformedResponseUriError -> {
				discardAuthorizationFlow()
				isAuthorizationInProgress = false
				val exception = MalformedResponseUriException()
				return Result.failure(exception)
			}

			is AuthorizationResponse.Cancellation -> {
				discardAuthorizationFlow()
				isAuthorizationInProgress = false
				val exception = CancelledLoginException()
				return Result.failure(exception)
			}
		}
	}

	/**
	 * Forces a refresh of the current tokens
	 */
	suspend fun forceRefreshTokens(customHeaders: Map<String, String> = emptyMap()): Result<AuthTokens> {
		return if (isAuthorized()) {
			// Shares the lock with the token handlers, so this never runs in parallel with an automatic refresh
			withContext(NonCancellable) {
				authRepository.tokenRefreshLock.withLock {
					authRepository.refreshTokens(idpConfig, customHeaders)
				}
			}
		} else {
			Result.failure(UnauthorizedException())
		}
	}

	/**
	 * Clears the current authorization state, effectively logging the user out.
	 * If [keepMetadata] is set to false, this also clears the provider metadata, requiring it to be loaded again before starting a new auth flow
	 */
	fun clearAuthState(
		keepMetadata: Boolean,
	) {
		authRepository.clearAuthTokens()
		if (!keepMetadata) {
			authRepository.clearProviderMetadata()
		}
	}

	private suspend fun fetchMetadata(
		forceFetch: Boolean = false,
		customHeaders: Map<String, String> = emptyMap(),
	): ProviderMetadata? {
		if (forceFetch || authRepository.getProviderMetadata() == null) {
			authRepository.loadProviderMetadata(idpConfig.discoveryUrl, customHeaders)
		}

		return authRepository.getProviderMetadata()
	}

	private fun requireAuthorizationFlow() = authorizationFlow
		?: throw AuthorizationException(AuthorizationErrorType.GENERAL, "Authorization flow was not yet started")

	/**
	 * Drops the current flow and releases its DPoP key. Use this instead of clearing [authorizationFlow] directly on
	 * every path that abandons a flow without it producing tokens — the key is created when the authorization starts, so a
	 * dropped flow whose key is not released leaves an entry in the secure storage behind.
	 */
	private fun discardAuthorizationFlow() {
		authorizationFlow?.discardDpopKey()
		authorizationFlow = null
	}

}
