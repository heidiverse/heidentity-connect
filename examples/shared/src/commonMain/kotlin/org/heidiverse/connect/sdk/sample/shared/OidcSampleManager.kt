package org.heidiverse.connect.sdk.sample.shared

import com.eygraber.uri.Uri
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import org.heidiverse.connect.sdk.AuthorizationController
import org.heidiverse.connect.sdk.browser.AuthorizationRequestLauncher
import org.heidiverse.connect.sdk.data.IdpConfig
import org.heidiverse.connect.sdk.exception.AuthorizationException
import org.heidiverse.connect.sdk.exception.HeidentityConnectException
import org.heidiverse.connect.sdk.extensions.browserless.BrowserlessAuthorizationController
import org.heidiverse.connect.sdk.extensions.browserless.BrowserlessException
import org.heidiverse.connect.sdk.extensions.browserless.BrowserlessSavedAuthFlow
import org.heidiverse.connect.sdk.extensions.ktor.browserless.KtorBrowserlessSavedAuthFlow
import org.heidiverse.connect.sdk.networking.authorization.AuthorizationResponse

/**
 * Holds the state of the sample app and drives the login flow, either in the browser through the
 * [AuthorizationController] or App2App through the [BrowserlessAuthorizationController].
 * Shared between the Android and the iOS sample app.
 *
 * Both controllers store their tokens in the same place, so the tokens of either login show up in [loginState], and
 * refreshing and logging out work the same for both.
 */
class OidcSampleManager(
	private val storage: IdpConfigStorage,
	private val app2appFlowStorage: App2AppFlowStorage,
	private val scope: CoroutineScope,
) {

	private val authController = AuthorizationController(storage.idpConfig.forBrowserLogin())

	/**
	 * Restores the App2App login in progress, if there is one. This is what lets the login finish when the callback
	 * arrives in a new process, after the OS killed the app while the user was in the user-interacting app.
	 */
	private val app2appController = BrowserlessAuthorizationController(
		idpConfig = storage.idpConfig,
		customSchemaLookup = SampleApp2AppConfig.customSchemeLookup,
		savedAuthFlow = restoreApp2AppFlow(),
		additionalTrustedHosts = SampleApp2AppConfig.additionalTrustedHosts,
		onPreDeepLink = { savedFlow -> saveApp2AppFlow(savedFlow) },
	)

	private val idpConfigMutable = MutableStateFlow(storage.idpConfig.toState())
	val idpConfig: StateFlow<IdpConfigState> = idpConfigMutable.asStateFlow()

	private val loginStateMutable = MutableStateFlow(LoginState())
	val loginState: StateFlow<LoginState> = loginStateMutable.asStateFlow()

	init {
		startIdpDiscovery()

		scope.launch {
			// Observe the tokens rather than the authorized state, so refreshed tokens show up as well
			authController.getAuthTokenFlow().collect { tokens ->
				loginStateMutable.update {
					it.copy(isLoggedIn = tokens != null, tokens = tokens?.toSampleTokens())
				}
			}
		}
	}

	fun setIdpConfig(config: IdpConfigState) {
		val idpConfig = config.toIdpConfig()
		storage.idpConfig = idpConfig
		idpConfigMutable.value = idpConfig.toState()
		authController.updateIdpConfig(idpConfig.forBrowserLogin())
		app2appController.updateIdpConfig(idpConfig)
		// A login started against the previous IDP can't be finished anymore
		app2appFlowStorage.savedAuthFlow = null
		startIdpDiscovery()
	}

	fun startIdpDiscovery() {
		runWithLoadingState {
			authController.discoverProvider(forceFetch = true)
		}
	}

	fun startLogin(launcher: AuthorizationRequestLauncher, scope: String, prompt: String) {
		runWithLoadingState {
			authController.startAuthorization(
				authRequestLauncher = launcher,
				scope = scope.trim(),
				prompt = prompt.trim().takeIf { it.isNotEmpty() },
			)
		}
	}

	fun handleAuthorizationResponse(response: AuthorizationResponse) {
		runWithLoadingState {
			authController.handleAuthorizationResponse(response)
		}
	}

	/**
	 * Starts the App2App login. The authorization request is sent by the SDK itself, and the authorization server
	 * answers with a deep link into the user-interacting app, which the SDK then opens. The login continues in
	 * [handleApp2AppCallback] once that app opens the native callback URI.
	 */
	fun startApp2AppLogin(scope: String, prompt: String) {
		runWithLoadingState {
			app2appController.startAuthorization(
				scope = scope.trim(),
				prompt = prompt.trim().takeIf { it.isNotEmpty() },
			).recoverCatching { error ->
				throw if (error is BrowserlessException && error.reason == BrowserlessException.Reason.APP_NOT_INSTALLED) {
					HeidentityConnectException(
						errorType = error.errorType,
						message = "${error.message}. Is the user-interacting app installed, and is its deep link " +
							"listed in <queries> in AndroidManifest.xml (Android) or in LSApplicationQueriesSchemes " +
							"(iOS)?",
						cause = error,
						errorCode = error.errorCode,
					)
				} else {
					error
				}
			}
		}
	}

	/**
	 * Handles a deep link into the app. If it is the native callback URI, the user-interacting app hands the login back
	 * with a `redirect_uri` parameter, or with an `error` if the user cancelled or the login failed. The app then calls
	 * that redirect_uri to get the authorization code, which the SDK exchanges for the tokens.
	 *
	 * Deep links that aren't the native callback URI are ignored.
	 */
	fun handleApp2AppCallback(url: String) {
		val idpConfig = idpConfigMutable.value
		val nativeCallbackUri = idpConfig.nativeCallbackUri.trim().takeIf { it.isNotEmpty() } ?: return
		val callback = Uri.parseOrNull(url)?.takeIf { it.isSameEndpointAs(nativeCallbackUri) } ?: return

		runWithLoadingState {
			runCatching {
				val redirectUri = callback.getQueryParameter("redirect_uri")
					?: throw if (callback.getQueryParameter("error") != null) {
						AuthorizationException.fromOauthRedirect(url)
					} else {
						IllegalArgumentException("The App2App callback has neither a redirect_uri nor an error: $url")
					}

				// The SDK only calls the redirect_uri if it points to a trusted host
				app2appController.handleCallback(redirectUri).getOrThrow()
			}.also {
				// Whatever the outcome, this flow is over
				app2appFlowStorage.savedAuthFlow = null
			}
		}
	}

	fun refreshTokens() {
		runWithLoadingState {
			authController.forceRefreshTokens()
		}
	}

	fun logout() {
		authController.clearAuthState(keepMetadata = true)
	}

	/**
	 * Operations can overlap, e.g. the discovery at startup and an App2App callback that started the app. The state
	 * keeps loading until the last one is done, and an operation that succeeds doesn't clear the error of another one.
	 * Only ever called on the main thread, so the counter needs no synchronization.
	 */
	private var runningOperations = 0

	private fun runWithLoadingState(block: suspend () -> Result<*>) {
		scope.launch {
			runningOperations++
			loginStateMutable.update { it.copy(isLoading = true, errorMessage = null) }
			val errorMessage = try {
				block().exceptionOrNull()?.toErrorMessage()
			} finally {
				runningOperations--
			}
			loginStateMutable.update {
				it.copy(isLoading = runningOperations > 0, errorMessage = errorMessage ?: it.errorMessage)
			}
		}
	}

	private fun restoreApp2AppFlow(): BrowserlessSavedAuthFlow? {
		val savedFlow = app2appFlowStorage.savedAuthFlow ?: return null
		// A flow saved by an incompatible version of the app can't be finished, the user just has to log in again
		return runCatching { json.decodeFromString(KtorBrowserlessSavedAuthFlow.serializer(), savedFlow) }.getOrNull()
	}

	private fun saveApp2AppFlow(savedFlow: BrowserlessSavedAuthFlow) {
		// HeidentityConnectBrowserlessKtor always saves a KtorBrowserlessSavedAuthFlow
		val ktorFlow = savedFlow as? KtorBrowserlessSavedAuthFlow ?: return
		app2appFlowStorage.savedAuthFlow = json.encodeToString(KtorBrowserlessSavedAuthFlow.serializer(), ktorFlow)
	}

	private companion object {

		val json = Json { ignoreUnknownKeys = true }

		/**
		 * The browser login must not send a native_callback_uri: an authorization server that supports App2App could
		 * otherwise treat it as an App2App request
		 */
		fun IdpConfig.forBrowserLogin() = copy(nativeCallbackUri = null)

		/** Compares scheme, host and path, the callback adds its parameters to the query */
		fun Uri.isSameEndpointAs(other: String): Boolean {
			val expected = Uri.parseOrNull(other) ?: return false
			return scheme.equals(expected.scheme, ignoreCase = true) &&
				host.equals(expected.host, ignoreCase = true) &&
				path.orEmpty().trimEnd('/') == expected.path.orEmpty().trimEnd('/')
		}

	}

}
