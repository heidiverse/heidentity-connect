package org.heidiverse.connect.sdk.sample.shared

import org.heidiverse.connect.sdk.browser.WebAuthenticationSessionFactory
import org.heidiverse.connect.sdk.browser.WebAuthenticationSessionLauncher
import platform.AuthenticationServices.ASPresentationAnchor
import platform.AuthenticationServices.ASWebAuthenticationPresentationContextProvidingProtocol
import platform.AuthenticationServices.ASWebAuthenticationSession
import platform.AuthenticationServices.ASWebAuthenticationSessionCallback
import platform.Foundation.NSError
import platform.Foundation.NSURL
import platform.UIKit.UIApplication
import platform.UIKit.UIWindow
import platform.UIKit.UIWindowScene
import platform.darwin.NSObject

/**
 * Starts the login in an ASWebAuthenticationSession and passes the response back to the [manager]
 */
class IosAuthorizationPresenter internal constructor(
	private val manager: OidcSampleManager,
) {

	private val launcher = WebAuthenticationSessionLauncher(
		presentationContextProvider = KeyWindowPresentationContextProvider(),
		ephemeralBrowserSession = false,
		responseReceivedHandler = { response ->
			manager.handleAuthorizationResponse(response)
		},
		sessionFactory = HttpsWebAuthenticationSessionFactory(),
	)

	fun startLogin(scope: String, prompt: String) {
		manager.startLogin(launcher, scope, prompt)
	}

}

private class KeyWindowPresentationContextProvider : NSObject(), ASWebAuthenticationPresentationContextProvidingProtocol {

	override fun presentationAnchorForWebAuthenticationSession(session: ASWebAuthenticationSession): ASPresentationAnchor {
		for (scene in UIApplication.sharedApplication.connectedScenes) {
			val windowScene = scene as? UIWindowScene ?: continue
			val keyWindow = windowScene.windows
				.filterIsInstance<UIWindow>()
				.firstOrNull { it.isKeyWindow() }
			if (keyWindow != null) return keyWindow
		}
		return UIWindow()
	}

}

/**
 * Creates sessions for HTTPS redirect URIs, which the [WebAuthenticationSessionLauncher] does not support by default.
 * The redirect host must be listed as a `webcredentials` associated domain of the app (see OidcSample.entitlements).
 * Custom scheme redirect URIs return null here and use the launcher's default session.
 */
private class HttpsWebAuthenticationSessionFactory : WebAuthenticationSessionFactory {

	private var session: ASWebAuthenticationSession? = null

	override fun createSession(
		authRequestUrl: NSURL,
		redirectCallbackUrl: NSURL?,
		completionHandler: (NSURL?, NSError?) -> Unit,
	): ASWebAuthenticationSession? {
		if (redirectCallbackUrl?.scheme != "https") return null

		val host = redirectCallbackUrl.host ?: return null
		val path = redirectCallbackUrl.path ?: return null
		val callback = ASWebAuthenticationSessionCallback.callbackWithHTTPSHost(host, path)

		session = ASWebAuthenticationSession(
			uRL = authRequestUrl,
			callback = callback,
			completionHandler = { callbackUrl, error ->
				session = null
				completionHandler(callbackUrl, error)
			},
		)
		return session
	}

}
