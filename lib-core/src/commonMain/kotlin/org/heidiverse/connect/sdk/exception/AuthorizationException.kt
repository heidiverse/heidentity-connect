package org.heidiverse.connect.sdk.exception

import com.eygraber.uri.Url

open class AuthorizationException(
	override val errorType: AuthorizationErrorType,
	message: String? = null,
	cause: Throwable? = null,
	errorCode: Int = HeidentityConnectErrorCode.typed(errorType.errorCode, HeidentityConnectErrorCode.OFFSET_AUTHORIZATION),
) : HeidentityConnectException(errorType, message, cause, errorCode) {

	companion object {
		fun fromOauthRedirect(redirectUri: String): AuthorizationException {
			val url = Url.parse(redirectUri)
			val error = url.getQueryParameter("error")
			val errorDescription = url.getQueryParameter("error_description")
			val errorUri = url.getQueryParameter("error_uri")

			val exceptionMessage = mapOf(
				"Error" to error,
				"Error description" to errorDescription,
				"Error URI" to errorUri,
			).mapNotNull { (label, value) ->
				value?.let { "$label: $it" }
			}.joinToString()
			return AuthorizationException(
				errorType = AuthorizationErrorType.AUTHORIZATION,
				message = exceptionMessage,
			)
		}
	}

}
