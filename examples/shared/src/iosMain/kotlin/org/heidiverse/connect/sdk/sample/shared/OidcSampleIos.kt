package org.heidiverse.connect.sdk.sample.shared

import kotlinx.coroutines.MainScope
import org.heidiverse.connect.sdk.extensions.ktor.HeidentityConnectBrowserlessKtor
import org.heidiverse.connect.sdk.ktor.HeidentityConnectKtor

/**
 * Entry point for the iOS sample app
 */
object OidcSampleIos {

	/**
	 * Initializes the OIDC SDK, including the browserless extension for the App2App login. Must be called once at app
	 * start, before any manager is created.
	 */
	fun initialize() {
		HeidentityConnectKtor().initialize()
		HeidentityConnectBrowserlessKtor.initialize()
	}

	fun createManager(): OidcSampleManager = OidcSampleManager(
		storage = IdpConfigStorage(),
		app2appFlowStorage = App2AppFlowStorage(),
		scope = MainScope(),
	)

	fun createAuthorizationPresenter(manager: OidcSampleManager) = IosAuthorizationPresenter(manager)

}
