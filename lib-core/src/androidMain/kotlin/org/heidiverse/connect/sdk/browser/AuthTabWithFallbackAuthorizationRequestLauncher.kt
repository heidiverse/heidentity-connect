package org.heidiverse.connect.sdk.browser

import android.content.Context
import org.heidiverse.connect.sdk.data.IdpConfig

/**
 * The default [AuthorizationRequestLauncher] on Android. It presents the authorization request in an Auth Tab if that is
 * possible and falls back to a Custom Tab otherwise.
 *
 * An Auth Tab is used if the browser supports one (e.g. Chrome 137 or later) and the redirect URI of the [IdpConfig] is one
 * an Auth Tab can intercept, i.e. a custom scheme or an HTTPS URI. The browser then returns the authorization response
 * directly as an activity result. In every other case the request goes through a Custom Tab and the response arrives as a
 * deeplink, so the app manifest always needs the intent filter for the [RedirectUriReceiverActivity].
 *
 * @param contextProvider A provider function for the [Context], so this launcher can be used from either an Activity or Fragment context.
 * @param authTabLauncher The launcher used if the authorization request can be presented in an Auth Tab
 * @param customTabLauncher The launcher used as a fallback
 */
class AuthTabWithFallbackAuthorizationRequestLauncher(
	private val contextProvider: () -> Context,
	private val authTabLauncher: AuthorizationRequestLauncher,
	private val customTabLauncher: AuthorizationRequestLauncher,
) : AuthorizationRequestLauncher {

	private var activeLauncher: AuthorizationRequestLauncher? = null

	override suspend fun launchAuthorizationRequest(
		authRequestUrl: String,
		idpConfig: IdpConfig,
		customHeaders: Map<String, String>,
	) {
		val canLaunchAuthTab = AuthorizationIntentManager.canLaunchAuthTab(
			context = contextProvider.invoke(),
			redirectUri = idpConfig.redirectUri,
		)
		val launcher = if (canLaunchAuthTab) authTabLauncher else customTabLauncher

		activeLauncher = launcher
		launcher.launchAuthorizationRequest(authRequestUrl, idpConfig, customHeaders)
	}

	override fun dismiss() {
		activeLauncher?.dismiss()
	}

}
