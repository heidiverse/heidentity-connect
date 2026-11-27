package org.heidiverse.connect.sdk.extensions

import androidx.activity.result.contract.ActivityResultContracts
import androidx.browser.auth.AuthTabIntent
import androidx.fragment.app.Fragment
import org.heidiverse.connect.sdk.AuthorizationController
import org.heidiverse.connect.sdk.browser.AuthTabAuthorizationRequestLauncher
import org.heidiverse.connect.sdk.browser.AuthTabWithFallbackAuthorizationRequestLauncher
import org.heidiverse.connect.sdk.browser.AuthorizationRequestLauncher
import org.heidiverse.connect.sdk.browser.BrowserAuthorizationRequestLauncher
import org.heidiverse.connect.sdk.networking.authorization.AuthorizationResponse

/**
 * Registers an [AuthorizationRequestLauncher] that will handle the authorization request flow.
 * The [onResponse] callback will be invoked when the authorization request flow completes and its response value should be passed on to [AuthorizationController.handleAuthorizationResponse]
 *
 * The authorization request is presented in an Auth Tab where possible and in a Custom Tab otherwise,
 * see [AuthTabWithFallbackAuthorizationRequestLauncher].
 *
 * @param useEphemeralSession True to use an ephemeral browser session, if supported by the browser
 */
fun Fragment.registerAuthorizationRequestLauncher(
	useEphemeralSession: Boolean = false,
	onResponse: (AuthorizationResponse) -> Unit,
): AuthorizationRequestLauncher {
	// Both result launchers have to be registered unconditionally before the fragment is created, because the fallback to a
	// Custom Tab is only resolved once an authorization request is launched
	val customTabResultLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
		result.toAuthorizationResponse()?.let(onResponse)
	}
	val authTabResultLauncher = AuthTabIntent.registerActivityResultLauncher(this) { result ->
		onResponse(result.toAuthorizationResponse())
	}

	return AuthTabWithFallbackAuthorizationRequestLauncher(
		contextProvider = { requireContext() },
		authTabLauncher = AuthTabAuthorizationRequestLauncher(
			contextProvider = { requireContext() },
			activityResultLauncher = authTabResultLauncher,
			useEphemeralSession = useEphemeralSession,
		),
		customTabLauncher = BrowserAuthorizationRequestLauncher(
			contextProvider = { requireContext() },
			activityResultLauncher = customTabResultLauncher,
			useEphemeralSession = useEphemeralSession,
		),
	)
}
