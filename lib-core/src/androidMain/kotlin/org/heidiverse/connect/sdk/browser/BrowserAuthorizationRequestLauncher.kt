package org.heidiverse.connect.sdk.browser

import android.content.Context
import android.content.Intent
import androidx.activity.result.ActivityResultLauncher
import androidx.core.net.toUri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.heidiverse.connect.sdk.data.IdpConfig

/**
 * @param contextProvider A provider function for the [Context], so this launcher can be used from either an Activity or Fragment context.
 * @param activityResultLauncher The [ActivityResultLauncher] to use for launching the authorization request
 * @param useEphemeralSession True to use an ephemeral browser session, if supported by the browser
 */
class BrowserAuthorizationRequestLauncher(
	private val contextProvider: () -> Context,
	private val activityResultLauncher: ActivityResultLauncher<Intent>,
	private val useEphemeralSession: Boolean = false,
) : AuthorizationRequestLauncher {

	override suspend fun launchAuthorizationRequest(
		authRequestUrl: String,
		idpConfig: IdpConfig,
		customHeaders: Map<String, String>
	) {
		val context = contextProvider.invoke()
		val service = AuthorizationIntentManager(context)
		val authRequestUri = authRequestUrl.toUri()
		val authRequestIntent = service.createAuthorizationRequestIntent(
			authRequestUri,
			service.createCustomTabsIntent(listOf(authRequestUri), useEphemeralSession),
		)

		withContext(Dispatchers.Main) {
			activityResultLauncher.launch(authRequestIntent)
		}
	}

	override fun dismiss() {
		// Not supported by Custom tabs
	}

}