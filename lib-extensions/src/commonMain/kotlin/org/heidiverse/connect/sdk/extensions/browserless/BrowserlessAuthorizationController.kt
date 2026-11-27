package org.heidiverse.connect.sdk.extensions.browserless

import com.eygraber.uri.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.withContext
import org.heidiverse.connect.sdk.AuthorizationController
import org.heidiverse.connect.sdk.browser.AuthorizationRequestLauncher
import org.heidiverse.connect.sdk.data.AuthTokens
import org.heidiverse.connect.sdk.data.IdpConfig
import org.heidiverse.connect.sdk.di.HeidentityConnectKoinComponent
import org.heidiverse.connect.sdk.extensions.browserless.networking.BrowserlessAuthorizationResponse
import org.heidiverse.connect.sdk.extensions.browserless.networking.BrowserlessAuthorizationService
import org.heidiverse.connect.sdk.extensions.browserless.utils.DeepLinkUtil
import org.heidiverse.connect.sdk.extensions.common.CommonCookieStore
import org.heidiverse.connect.sdk.extensions.common.CookieStores
import org.heidiverse.connect.sdk.networking.authorization.AuthorizationResponse
import org.koin.core.component.inject
import org.koin.core.qualifier.named

/**
 * @param additionalTrustedHosts The hosts the redirect_uri passed to [handleCallback] may point to, in addition to the
 * host of the discovery URL. That redirect_uri comes from another app, and the SDK calls it to continue the flow, so
 * it must only be accepted if it belongs to a trusted authorization server. Add a host here if the authorization
 * server federates the login to another one.
 */
class BrowserlessAuthorizationController(
    private var idpConfig: IdpConfig,
    private val customSchemaLookup: Map<String, String> = mapOf(),
    savedAuthFlow: BrowserlessSavedAuthFlow? = null,
    additionalTrustedHosts: Set<String> = emptySet(),
    private val onPreDeepLink: (BrowserlessSavedAuthFlow) -> Unit = {}
) : HeidentityConnectKoinComponent {
    private val additionalTrustedHosts = additionalTrustedHosts.mapTo(mutableSetOf()) { it.trim().lowercase() }

    private val controller = AuthorizationController(idpConfig, savedAuthFlow?.savedAuthorizationFlow)
    private val authService by inject<BrowserlessAuthorizationService>()
    private val deepLinkUtil by inject<DeepLinkUtil>()
    private val cookieStore by inject<CommonCookieStore>(named(CookieStores.Browserless))

    private val authFlowSaver by inject<BrowserlessAuthFlowSaver>()

    private val nativeLauncher = object : AuthorizationRequestLauncher {
        override suspend fun launchAuthorizationRequest(
            authRequestUrl: String,
            idpConfig: IdpConfig,
            customHeaders: Map<String, String>
        ) {
            processResponse(authService.authorize(authRequestUrl, customHeaders), customHeaders = customHeaders)
        }

        override fun dismiss() {}
    }

    init {
        savedAuthFlow?.cookieStoreData?.let {
            cookieStore.restore(it)
        }
    }

    fun isAuthorized() = controller.isAuthorized()

    fun getAuthorizedState() = controller.getAuthorizedState()

    fun getAuthTokens() = controller.getAuthTokens()

    fun getSessionExpiration() = controller.getSessionExpiration()

    fun updateIdpConfig(idpConfig: IdpConfig) {
        clearAuthState(keepMetadata = false)
        controller.updateIdpConfig(idpConfig)
        this.idpConfig = idpConfig
    }

    fun clearAuthState(keepMetadata: Boolean) {
        cookieStore.clear()
        controller.clearAuthState(keepMetadata)
    }

    suspend fun discoverProvider(
        forceFetch: Boolean = false,
        customHeaders: Map<String, String> = emptyMap()
    ) =
        controller.discoverProvider(forceFetch, customHeaders)

    suspend fun startAuthorization(
        scope: String,
        prompt: String? = null,
        uiLocales: String? = null,
        loginHint: String? = null,
        customHeaders: Map<String, String> = emptyMap()
    ): Result<Unit> =
        controller.startAuthorization(
            authRequestLauncher = nativeLauncher,
            scope = scope,
            prompt = prompt,
            uiLocales = uiLocales,
            loginHint = loginHint,
            customHeaders = customHeaders,
        )

    suspend fun handleCallback(
        redirectUri: String,
        tokenVerificationHook: suspend (AuthTokens) -> Result<Unit> = { Result.success(Unit) },
        customHeaders: Map<String, String> = emptyMap(),
    ): Result<Unit> = runCatching {
        requireTrustedHost(redirectUri)
        processResponse(authService.authorize(redirectUri, customHeaders), tokenVerificationHook, customHeaders)
    }

    /**
     * The redirect_uri comes from another app, and the SDK sends a request to it. The spec therefore requires the app
     * to only accept URLs of authorization servers it trusts.
     */
    private fun requireTrustedHost(redirectUri: String) {
        val discoveryHost = Uri.parseOrNull(idpConfig.discoveryUrl.trim())?.host?.lowercase()
        val trustedHosts = additionalTrustedHosts + listOfNotNull(discoveryHost)

        val redirect = Uri.parseOrNull(redirectUri)
        val isHttp = redirect?.scheme?.lowercase() in setOf("https", "http")
        val host = redirect?.host?.lowercase()
        if (!isHttp || host == null || host !in trustedHosts) {
            throw BrowserlessException(
                reason = BrowserlessException.Reason.UNTRUSTED_REDIRECT_URI,
                message = "The redirect_uri does not point to a trusted host: $redirectUri. Add its host to " +
                    "additionalTrustedHosts if it belongs to a trusted authorization server."
            )
        }
    }

    private suspend fun processResponse(
        response: BrowserlessAuthorizationResponse,
        tokenVerificationHook: suspend (AuthTokens) -> Result<Unit> = { Result.success(Unit) },
        customHeaders: Map<String, String>
    ) {
        when (response) {
			is BrowserlessAuthorizationResponse.Action -> when (response.type) {
                BrowserlessAuthorizationResponse.Action.ActionType.DeepLink -> {
                    val customSchemaHint = customSchemaLookup[authService.getHostName(response.url)]

                    // Checking 'isAppInstalled' on iOS will always return true for universal links
                    // As such, provide a mechanism to map universal links to custom schemas if applicable
                    if (!deepLinkUtil.isAppInstalled(response.url)
                        || (customSchemaHint != null && !deepLinkUtil.isAppInstalled(customSchemaHint))) {
                        throw BrowserlessException(
                            reason = BrowserlessException.Reason.APP_NOT_INSTALLED,
                            message = "No app found that can handle the deep link"
                        )
                    }

                    // Call hook to store state
                    onPreDeepLink(authFlowSaver.save(controller.saveAuthorizationFlow()))

                    // Should not error given the check above
                    withContext(Dispatchers.Main) {
                        deepLinkUtil.openUri(response.url)
                    }
                }
                BrowserlessAuthorizationResponse.Action.ActionType.Call -> {
                    if (response.url.startsWith(idpConfig.redirectUri)) {
                        controller.handleAuthorizationResponse(
                            authResponse = AuthorizationResponse.from(response.url),
                            tokenVerificationHook = tokenVerificationHook,
                            customHeaders = customHeaders
                        ).getOrThrow()
                    } else {
                        processResponse(authService.authorize(response.url, customHeaders), customHeaders = customHeaders)
                    }
                }
            }
		}
    }
}