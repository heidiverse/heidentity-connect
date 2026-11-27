package org.heidiverse.connect.sdk.browser

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.browser.auth.AuthTabIntent
import androidx.browser.customtabs.CustomTabsClient
import androidx.browser.customtabs.CustomTabsIntent

internal class AuthorizationIntentManager(private val context: Context, private val browserServiceManager: BrowserServiceManager) {

	companion object {
		const val KEY_AUTHORIZATION_RESPONSE = "KEY_AUTHORIZATION_RESPONSE"

		/**
		 * Whether an authorization request redirecting to [redirectUri] can be presented in an Auth Tab, which requires both
		 * a browser that supports Auth Tabs and a redirect the Auth Tab is able to intercept.
		 *
		 * This only queries the [android.content.pm.PackageManager], so it can be used to pick the flow before creating an
		 * instance and binding the browser service.
		 */
		fun canLaunchAuthTab(context: Context, redirectUri: String): Boolean {
			return BrowserServiceManager.isAuthTabSupported(context) && AuthTabRedirect.from(redirectUri) != null
		}
	}

	private val browserPackage: String? = CustomTabsClient.getPackageName(context, null)

	constructor(context: Context) : this(context, BrowserServiceManager(context))

	init {
		if (browserPackage != null) {
			browserServiceManager.bind(browserPackage)
		}
	}

	fun canLaunchAuthTab(redirectUri: String): Boolean {
		return browserServiceManager.isAuthTabSupported() && AuthTabRedirect.from(redirectUri) != null
	}

	fun createCustomTabsIntent(possibleUris: List<Uri>?, useEphemeralSession: Boolean): CustomTabsIntent {
		return browserServiceManager.createCustomTabsIntent(possibleUris, useEphemeralSession)
	}

	fun createAuthorizationRequestIntent(authRequestUri: Uri, customTabsIntent: CustomTabsIntent): Intent {
		val authIntent = prepareAuthorizationRequestIntent(authRequestUri, customTabsIntent)
		return AuthorizationActivity.createStartForResultIntent(context, authIntent)
	}

	/**
	 * Creates the [AuthTabIntent] for an authorization request. In contrast to [createAuthorizationRequestIntent] this is
	 * not wrapped in an [AuthorizationActivity] intent, because the browser returns the authorization response as an
	 * activity result instead of dispatching it as a deeplink.
	 */
	fun createAuthTabIntent(useEphemeralSession: Boolean): AuthTabIntent {
		val authTabIntent = browserServiceManager.createAuthTabIntent(useEphemeralSession)
		// Target the same browser the Auth Tab support was resolved for. An associated session already guarantees this, but
		// the service connection is established asynchronously, so there is not necessarily a session yet.
		authTabIntent.intent.setPackage(browserPackage)
		return authTabIntent
	}

	private fun prepareAuthorizationRequestIntent(authRequestUri: Uri, customTabsIntent: CustomTabsIntent): Intent {
		val intent = customTabsIntent.intent
		intent.setPackage(browserPackage)
		intent.data = authRequestUri
		return intent
	}

}
