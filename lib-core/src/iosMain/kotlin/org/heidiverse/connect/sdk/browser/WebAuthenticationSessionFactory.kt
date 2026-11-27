package org.heidiverse.connect.sdk.browser

import platform.AuthenticationServices.ASWebAuthenticationSession
import platform.Foundation.NSError
import platform.Foundation.NSURL

/**
 * Optional hook for host apps to provide a custom [ASWebAuthenticationSession].
 *
 * This allows iOS host code (e.g. Swift with #available(iOS 17.4, *)) to create
 * sessions using newer callback APIs while keeping the rest of the flow in KMP.
 */
interface WebAuthenticationSessionFactory {
	fun createSession(
		authRequestUrl: NSURL,
		redirectCallbackUrl: NSURL?,
		completionHandler: (NSURL?, NSError?) -> Unit,
	): ASWebAuthenticationSession?
}
