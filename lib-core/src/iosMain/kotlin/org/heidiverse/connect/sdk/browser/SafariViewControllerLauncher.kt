package org.heidiverse.connect.sdk.browser

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.heidiverse.connect.sdk.data.IdpConfig
import org.heidiverse.connect.sdk.networking.authorization.AuthorizationResponse
import platform.Foundation.NSURL
import platform.SafariServices.SFSafariViewController
import platform.SafariServices.SFSafariViewControllerConfiguration
import platform.SafariServices.SFSafariViewControllerDelegateProtocol
import platform.UIKit.*
import platform.darwin.NSObject

class SafariViewControllerLauncher(
	private val presentationContextProvider: UIViewController,
	private val controllerConfiguration: SFSafariViewControllerConfiguration,
	private val responseReceivedHandler: (AuthorizationResponse) -> Unit,
) : AuthorizationRequestLauncher {

	private lateinit var safariViewController: SFSafariViewController

	override suspend fun launchAuthorizationRequest(
		authRequestUrl: String,
		idpConfig: IdpConfig,
		customHeaders: Map<String, String>
	) {
		val openBrowserUrl = NSURL.URLWithString(authRequestUrl)
		if (openBrowserUrl != null) {
			val redirectCallbackUrl = NSURL.URLWithString(idpConfig.redirectUri)
			val isUniversalLink = redirectCallbackUrl?.scheme == "https"
			if (isUniversalLink) {
				println("ERROR: SFSafariViewController does not support universal urls. Consider using ASWebAuthenticationSession on iOS >= 17.4.")
				return
			}

			withContext(Dispatchers.Main) {
				safariViewController = SFSafariViewController(uRL = openBrowserUrl, controllerConfiguration)
				safariViewController.delegate = SafariViewControllerDelegate(responseReceivedHandler)
				safariViewController.modalPresentationStyle = UIModalPresentationPageSheet

				safariViewController.presentationController?.delegate = PresentationControllerDelegate(responseReceivedHandler)

				presentationContextProvider.presentViewController(
					safariViewController,
					animated = true,
					completion = null
				)
			}
		}
	}

	override fun dismiss() {
		presentationContextProvider.dismissViewControllerAnimated(true, null)
	}
}

class SafariViewControllerDelegate(
	private val responseReceivedHandler: (AuthorizationResponse) -> Unit,
) : NSObject(), SFSafariViewControllerDelegateProtocol {

	override fun safariViewControllerDidFinish(controller: SFSafariViewController) {
		responseReceivedHandler(AuthorizationResponse.Cancellation)
	}
}

class PresentationControllerDelegate(
	private val responseReceivedHandler: (AuthorizationResponse) -> Unit,
) : NSObject(), UIAdaptivePresentationControllerDelegateProtocol {

	override fun presentationControllerWillDismiss(presentationController: UIPresentationController) {
		responseReceivedHandler(AuthorizationResponse.Cancellation)
	}
}