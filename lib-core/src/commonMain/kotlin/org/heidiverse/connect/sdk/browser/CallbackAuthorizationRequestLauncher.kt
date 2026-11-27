package org.heidiverse.connect.sdk.browser

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.heidiverse.connect.sdk.data.IdpConfig

/**
 * Generic [AuthorizationRequestLauncher] implementation that forwards authorization requests to callbacks.
 *
 * This is useful for custom/native login flows where the app wants to receive the generated authorization URL
 * and present its own UI instead of using one of the browser-based launchers.
 */
class CallbackAuthorizationRequestLauncher(
	private val launchHandler: (
		authRequestUrl: String,
		idpConfig: IdpConfig,
		customHeaders: Map<String, String>,
	) -> Unit,
	private val dismissHandler: () -> Unit = {},
	private val dispatchOnMain: Boolean = true,
) : AuthorizationRequestLauncher {

	override suspend fun launchAuthorizationRequest(
		authRequestUrl: String,
		idpConfig: IdpConfig,
		customHeaders: Map<String, String>,
	) {
		if (dispatchOnMain) {
			withContext(Dispatchers.Main) {
				launchHandler(authRequestUrl, idpConfig, customHeaders)
			}
		} else {
			launchHandler(authRequestUrl, idpConfig, customHeaders)
		}
	}

	override fun dismiss() {
		dismissHandler()
	}
}
