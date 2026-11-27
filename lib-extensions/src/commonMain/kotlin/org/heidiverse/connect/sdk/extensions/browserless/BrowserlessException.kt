package org.heidiverse.connect.sdk.extensions.browserless

import org.heidiverse.connect.sdk.exception.HeidentityConnectErrorCode
import org.heidiverse.connect.sdk.exception.HeidentityConnectException

class BrowserlessException(
	val reason: Reason,
	message: String? = null,
	cause: Throwable? = null,
) : HeidentityConnectException(
	errorType = BrowserlessErrorType.AUTHORIZATION,
	message = message ?: reason.name,
	cause = cause,
	errorCode = HeidentityConnectErrorCode.typed(BrowserlessErrorType.AUTHORIZATION.errorCode, reason.classOffset),
) {

	enum class Reason {
		/**
		 * No installed app can handle the deep link into the user-interacting app. On iOS, an https deep link counts as
		 * installed only if its host is mapped to a custom scheme in the customSchemaLookup of the
		 * [BrowserlessAuthorizationController].
		 */
		APP_NOT_INSTALLED,

		/** A URL returned by the authorization server could not be parsed. */
		MALFORMED_URL,

		/**
		 * The redirect_uri handed back by the user-interacting app is neither on the host of the discovery URL nor on
		 * one of the additionalTrustedHosts of the [BrowserlessAuthorizationController].
		 */
		UNTRUSTED_REDIRECT_URI,
		;

		val classOffset: Int
			get() = when (this) {
				APP_NOT_INSTALLED -> HeidentityConnectErrorCode.OFFSET_BROWSERLESS_APP_NOT_INSTALLED
				MALFORMED_URL -> HeidentityConnectErrorCode.OFFSET_BROWSERLESS_MALFORMED_URL
				UNTRUSTED_REDIRECT_URI -> HeidentityConnectErrorCode.OFFSET_BROWSERLESS_UNTRUSTED_REDIRECT_URI
			}
	}
}
