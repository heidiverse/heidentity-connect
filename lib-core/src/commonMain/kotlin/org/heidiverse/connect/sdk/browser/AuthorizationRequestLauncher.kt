package org.heidiverse.connect.sdk.browser

import org.heidiverse.connect.sdk.AuthorizationController
import org.heidiverse.connect.sdk.data.IdpConfig

interface AuthorizationRequestLauncher {

	/**
	 * Launch the authorization request from the [authRequestUrl]. After receiving the authorization code, the [AuthorizationController.handleAuthorizationResponse] should be called.
	 */
	suspend fun launchAuthorizationRequest(authRequestUrl: String, idpConfig: IdpConfig, customHeaders: Map<String, String> = emptyMap())

	/**
	 * Dismiss view controller (browser, custom-tab,...)
	 */
	fun dismiss()

}