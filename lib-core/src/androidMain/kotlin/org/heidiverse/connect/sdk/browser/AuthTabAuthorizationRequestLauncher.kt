package org.heidiverse.connect.sdk.browser

import android.content.Context
import android.content.Intent
import androidx.activity.result.ActivityResultLauncher
import androidx.browser.auth.AuthTabIntent
import androidx.core.net.toUri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.heidiverse.connect.sdk.data.IdpConfig
import org.heidiverse.connect.sdk.exception.AuthorizationErrorType
import org.heidiverse.connect.sdk.exception.AuthorizationException

/**
 * An [AuthorizationRequestLauncher] that presents the authorization request in an Auth Tab.
 *
 * In contrast to the [BrowserAuthorizationRequestLauncher], the browser intercepts the redirect itself and hands it back as
 * an activity result instead of letting the system dispatch it as a deeplink.
 *
 * An Auth Tab can only be used if the browser supports it and the redirect URI is one it can intercept, so prefer
 * [AuthTabWithFallbackAuthorizationRequestLauncher], which checks both via [canLaunchAuthTab] and falls back to a Custom
 * Tab otherwise.
 *
 * @param contextProvider A provider function for the [Context], so this launcher can be used from either an Activity or Fragment context.
 * @param activityResultLauncher The [ActivityResultLauncher] to use for launching the authorization request, created by [AuthTabIntent.registerActivityResultLauncher]
 * @param useEphemeralSession True to use an ephemeral browser session, if supported by the browser
 */
class AuthTabAuthorizationRequestLauncher(
	private val contextProvider: () -> Context,
	private val activityResultLauncher: ActivityResultLauncher<Intent>,
	private val useEphemeralSession: Boolean = false,
) : AuthorizationRequestLauncher {

	companion object {

		/**
		 * @return True if an authorization request redirecting to [redirectUri] can be presented in an Auth Tab, false if it
		 * has to be presented in a Custom Tab instead.
		 */
		fun canLaunchAuthTab(context: Context, redirectUri: String): Boolean {
			return AuthorizationIntentManager.canLaunchAuthTab(context, redirectUri)
		}

	}

	override suspend fun launchAuthorizationRequest(
		authRequestUrl: String,
		idpConfig: IdpConfig,
		customHeaders: Map<String, String>
	) {
		val context = contextProvider.invoke()
		val service = AuthorizationIntentManager(context)
		val redirect = AuthTabRedirect.from(idpConfig.redirectUri)
			?: throw AuthorizationException(
				errorType = AuthorizationErrorType.AUTHORIZATION,
				message = "The redirect URI '${idpConfig.redirectUri}' cannot be intercepted by an Auth Tab, " +
					"use an https redirect or a custom scheme",
			)
		val authRequestUri = authRequestUrl.toUri()
		val authTabIntent = service.createAuthTabIntent(useEphemeralSession)

		withContext(Dispatchers.Main) {
			when (redirect) {
				is AuthTabRedirect.CustomScheme -> authTabIntent.launch(activityResultLauncher, authRequestUri, redirect.scheme)
				is AuthTabRedirect.Https -> authTabIntent.launch(activityResultLauncher, authRequestUri, redirect.host, redirect.path)
			}
		}
	}

	override fun dismiss() {
		// Not supported by Auth tabs
	}

}
