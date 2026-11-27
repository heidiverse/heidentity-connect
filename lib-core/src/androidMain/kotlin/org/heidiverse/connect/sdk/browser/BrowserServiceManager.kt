package org.heidiverse.connect.sdk.browser

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import androidx.browser.auth.AuthTabIntent
import androidx.browser.auth.AuthTabSession
import androidx.browser.customtabs.*
import androidx.core.net.toUri
import androidx.core.os.bundleOf

/**
 * Manages the connection to the browser service and creates the intents to present an authorization request in it.
 *
 * The bound [CustomTabsClient] is the factory for both Custom Tab and Auth Tab sessions, so a single connection serves
 * both [createCustomTabsIntent] and [createAuthTabIntent].
 */
internal class BrowserServiceManager(private val context: Context) {

	companion object {

		/**
		 * @return True if the browser used for Custom Tabs also supports Auth Tabs, false otherwise.
		 * This only queries the [PackageManager] and does not require a bound service.
		 */
		fun isAuthTabSupported(context: Context): Boolean {
			val browserPackage = CustomTabsClient.getPackageName(context, null) ?: return false
			return CustomTabsClient.isAuthTabSupported(context, browserPackage)
		}

	}

	private var customTabsClient: CustomTabsClient? = null
	private var customTabsConnection: CustomTabsServiceConnection? = null

	fun bind(browserPackage: String) {
		if (customTabsConnection != null) return

		val connection = object : CustomTabsServiceConnection() {
			override fun onCustomTabsServiceConnected(name: ComponentName, client: CustomTabsClient) {
				client.warmup(0)
				customTabsClient = client
			}

			override fun onServiceDisconnected(name: ComponentName) {
				customTabsClient = null
			}
		}
		customTabsConnection = connection

		if (!CustomTabsClient.bindCustomTabsService(context, browserPackage, connection)) {
			// Failed to bind the connection to the custom tabs service, e.g. if the browser does not support custom tabs
			customTabsConnection = null
		}
	}

	fun dispose(context: Context) {
		val connection = customTabsConnection ?: return

		context.unbindService(connection)
		customTabsClient = null
	}

	/**
	 * @return True if the browser used for Custom Tabs also supports Auth Tabs, false otherwise.
	 */
	fun isAuthTabSupported(): Boolean = isAuthTabSupported(context)

	fun createCustomTabsIntent(possibleUris: List<Uri>?, useEphemeralSession: Boolean): CustomTabsIntent {
		val builder = CustomTabsIntent.Builder(createSession(possibleUris))

		if (useEphemeralSession) {
			val defaultBrowser = getDefaultBrowser()
			val ephemeralBrowser = getEphemeralSupportedBrowser()

			if (ephemeralBrowser != null) {
				if (defaultBrowser == ephemeralBrowser) {
					// Default browser supports ephemeral browsing, so we can just enable it and use the default behavior
					return builder.setEphemeralBrowsingEnabled(true).build()
				} else {
					// A different browser supports ephemeral browsing, so we need to set it explicitly
					val customTabsIntent = builder.setEphemeralBrowsingEnabled(true).build()
					customTabsIntent.intent.setPackage(ephemeralBrowser)
					return customTabsIntent
				}
			} else {
				// Ephemeral browsing is requested, but no browser supports it. Fallback to the default behavior without ephemeral browsing.
				return builder.build()
			}
		} else {
			// Ephemeral browsing is not requested, so we can use the default behavior
			return builder.build()
		}
	}

	/**
	 * Creates the [AuthTabIntent] for an authorization request. Unlike a Custom Tab session, an [AuthTabSession] cannot
	 * prefetch the authorization URL, it only associates the intent with this client and the warmed up connection.
	 */
	fun createAuthTabIntent(useEphemeralSession: Boolean): AuthTabIntent {
		val builder = AuthTabIntent.Builder()
			.setEphemeralBrowsingEnabled(useEphemeralSession)

		// Associating the session also guarantees that the intent is delivered to the browser the session belongs to.
		// The service connection is established asynchronously, so there is no session for the first request yet.
		createAuthTabSession()?.let { builder.setSession(it) }

		return builder.build()
	}

	private fun createAuthTabSession(): AuthTabSession? {
		val client = customTabsClient ?: return null
		return client.newAuthTabSession(null, null)
	}

	private fun createSession(possibleUris: List<Uri>?): CustomTabsSession? {
		val client = customTabsClient ?: return null
		val session = client.newSession(null) ?: return null

		if (!possibleUris.isNullOrEmpty()) {
			val additionalUrls = possibleUris.drop(1).map {
				bundleOf(CustomTabsService.KEY_URL to it)
			}
			session.mayLaunchUrl(possibleUris.first(), null, additionalUrls)
		}

		return session
	}

	/**
	 * @return The package name of the browser that supports custom tabs and optionally ephemeral browsing, if requested.
	 */
	private fun getEphemeralSupportedBrowser(): String? {
		return getDefaultBrowser()?.takeIf { supportsEphemeralBrowsing(it) }
			?: getFallbackBrowser()?.takeIf { supportsEphemeralBrowsing(it) }
	}

	/**
	 * @return The package name of the preferred browser for custom tabs, or null if the default browser does not support custom tabs.
	 */
	private fun getDefaultBrowser(): String? {
		return CustomTabsClient.getPackageName(context, null)
	}

	/**
	 * @return The package name of any browser that supports custom tabs, or null if no installed browser supports custom tabs.
	 */
	private fun getFallbackBrowser(): String? {
		// The URL passed here doesn't matter, it just needs to be a valid URI, preferably HTTPS
		val activityIntent = Intent(Intent.ACTION_VIEW, "https://example.com".toUri())
		val resolveInfos = context.packageManager.queryIntentActivities(activityIntent, PackageManager.MATCH_ALL)
		val packageNames = resolveInfos.map { it.activityInfo.packageName }
		return CustomTabsClient.getPackageName(context, packageNames, true)
	}

	/**
	 * @return True if the [provider] supports ephemeral browsing, false otherwise.
	 */
	private fun supportsEphemeralBrowsing(provider: String): Boolean {
		return CustomTabsClient.isEphemeralBrowsingSupported(context, provider)
	}

}
