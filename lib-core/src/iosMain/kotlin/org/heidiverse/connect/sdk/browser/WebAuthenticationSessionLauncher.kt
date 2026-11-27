package org.heidiverse.connect.sdk.browser

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.heidiverse.connect.sdk.data.IdpConfig
import org.heidiverse.connect.sdk.exception.AuthorizationErrorType
import org.heidiverse.connect.sdk.networking.authorization.AuthorizationResponse
import platform.AuthenticationServices.ASWebAuthenticationPresentationContextProvidingProtocol
import platform.AuthenticationServices.ASWebAuthenticationSession
import platform.Foundation.NSError
import platform.Foundation.NSURL

class WebAuthenticationSessionLauncher(
	private val presentationContextProvider: ASWebAuthenticationPresentationContextProvidingProtocol,
	private val ephemeralBrowserSession: Boolean,
	private val responseReceivedHandler: (AuthorizationResponse) -> Unit,
	private val sessionFactory: WebAuthenticationSessionFactory? = null,
) : AuthorizationRequestLauncher {

	private lateinit var session: ASWebAuthenticationSession

	override suspend fun launchAuthorizationRequest(
		authRequestUrl: String,
		idpConfig: IdpConfig,
		customHeaders: Map<String, String>
	) {
		val openBrowserUrl = NSURL.URLWithString(authRequestUrl)
		if (openBrowserUrl != null) {

			val redirectCallbackUrl = NSURL.URLWithString(idpConfig.redirectUri)
			val redirectUrlScheme = redirectCallbackUrl?.scheme

			// Decide whether the callback is a universal link (https://) or a custom scheme
			val isUniversalLink = redirectUrlScheme == "https"

			val completionHandlerCallback: (NSURL?, NSError?) -> Unit = { callbackUrl, error ->

				val response = when {
					error != null -> {
						println("ERROR: ASWebAuthenticationSession completed with: $error")
						AuthorizationResponse.fromNsError(error)
					}

					callbackUrl != null -> {
						AuthorizationResponse.from(callbackUrl.toString())
					}

					else -> {
						AuthorizationResponse.MalformedResponseUriError
					}
				}
				responseReceivedHandler(response)
			}

			withContext(Dispatchers.Main) {
				val providedSession = sessionFactory?.createSession(
					authRequestUrl = openBrowserUrl,
					redirectCallbackUrl = redirectCallbackUrl,
					completionHandler = completionHandlerCallback
				)

				session = providedSession ?: run {
					if (isUniversalLink) {
						println("ERROR: This launcher only supports custom-scheme redirects by default. Universal-link callbacks require iOS 17.4+ callback APIs and should be provided via a custom WebAuthenticationSessionFactory.")
						return@withContext
					}

					// Uses the callbackURLScheme initializer to avoid static linkage to iOS 17.4+ callback symbols.
					ASWebAuthenticationSession(
						uRL = openBrowserUrl,
						callbackURLScheme = redirectUrlScheme,
						completionHandler = completionHandlerCallback
					)
				}

				if (providedSession == null) {
					session.prefersEphemeralWebBrowserSession = ephemeralBrowserSession
				}
				session.presentationContextProvider = presentationContextProvider

				session.start()
			}
		}
	}

	override fun dismiss() {
		session.cancel()
	}
}

private object ASWebAuthenticationSessionErrorCode {
	const val CANCELED_LOGIN = 1
	const val PRESENTATION_CONTEXT_NOT_PROVIDED = 2
	const val PRESENTATION_CONTEXT_INVALID = 3
}

private fun AuthorizationResponse.Companion.fromNsError(nsError: NSError): AuthorizationResponse {

	val aswebAuthErrorDomain = "com.apple.AuthenticationServices.WebAuthenticationSession"

	val errorType = AuthorizationErrorType.AUTHORIZATION
	val domain = nsError.domain
	val code = nsError.code.toInt()
	val message = nsError.localizedDescription

	return when (domain) {
		aswebAuthErrorDomain -> {
			when (code) {
				ASWebAuthenticationSessionErrorCode.CANCELED_LOGIN -> {
					AuthorizationResponse.Cancellation
				}

				ASWebAuthenticationSessionErrorCode.PRESENTATION_CONTEXT_NOT_PROVIDED -> {
					AuthorizationResponse.Error(errorType, "Presentation context not provided: $message")
				}

				ASWebAuthenticationSessionErrorCode.PRESENTATION_CONTEXT_INVALID -> {
					AuthorizationResponse.Error(errorType, "Presentation context invalid: $message")
				}

				else -> {
					AuthorizationResponse.Error(errorType, message)
				}
			}
		}

		else -> {
			// Fallback for an unknown domain
			AuthorizationResponse.Error(errorType, "Domain $domain, code $code, message: $message")
		}
	}

}
