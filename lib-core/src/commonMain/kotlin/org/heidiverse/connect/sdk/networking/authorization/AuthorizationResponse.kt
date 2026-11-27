package org.heidiverse.connect.sdk.networking.authorization

import com.eygraber.uri.Url
import kotlinx.serialization.Serializable
import org.heidiverse.connect.sdk.exception.AuthorizationErrorType
import org.heidiverse.connect.sdk.exception.AuthorizationException

@Serializable
sealed interface AuthorizationResponse {

	@Serializable
	data class Success(
		val code: String,
		val state: String?,
	) : AuthorizationResponse

	@Serializable
	data class Error(
		val errorType: AuthorizationErrorType,
		val message: String?,
	) : AuthorizationResponse

	@Serializable
	data object BrowserNotFoundError : AuthorizationResponse

	@Serializable
	data object MalformedResponseUriError : AuthorizationResponse

	@Serializable
	data object Cancellation : AuthorizationResponse

	companion object {

		private const val KEY_ERROR = "error"
		private const val KEY_CODE = "code"
		private const val KEY_STATE = "state"

		fun from(responseUri: String): AuthorizationResponse {
			val parsedResponseUri = Url.parse(responseUri)

			if (parsedResponseUri.getQueryParameterNames().contains(KEY_ERROR)) {
				// Parse error from response Uri according to https://datatracker.ietf.org/doc/html/rfc6749#section-4.1.2.1
				val exception = AuthorizationException.fromOauthRedirect(responseUri)
				return Error(exception.errorType, exception.message)
			} else {
				// Get the authorization code from the response Uri
				val code = parsedResponseUri.getQueryParameter(KEY_CODE)
				return if (code == null) {
					MalformedResponseUriError
				} else {
					val state = parsedResponseUri.getQueryParameter(KEY_STATE)
					Success(code, state)
				}
			}
		}
	}

}