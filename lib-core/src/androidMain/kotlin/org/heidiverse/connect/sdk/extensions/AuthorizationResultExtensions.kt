package org.heidiverse.connect.sdk.extensions

import android.app.Activity
import androidx.activity.result.ActivityResult
import androidx.browser.auth.AuthTabIntent
import kotlinx.serialization.json.Json
import org.heidiverse.connect.sdk.browser.AuthorizationIntentManager
import org.heidiverse.connect.sdk.exception.AuthorizationErrorType
import org.heidiverse.connect.sdk.networking.authorization.AuthorizationResponse

/**
 * Maps the result of a Custom Tab based authorization request to an [AuthorizationResponse],
 * or null if the result carries no response that needs to be handled.
 */
internal fun ActivityResult.toAuthorizationResponse(): AuthorizationResponse? {
	val authResponseString = data?.extras?.requireString(AuthorizationIntentManager.KEY_AUTHORIZATION_RESPONSE)
	val authResponse = authResponseString?.let { Json.decodeFromString<AuthorizationResponse>(it) }

	return when (resultCode) {
		Activity.RESULT_OK -> authResponse
		Activity.RESULT_CANCELED -> authResponse ?: AuthorizationResponse.Cancellation
		else -> null
	}
}

/**
 * Maps the result of an Auth Tab based authorization request to an [AuthorizationResponse].
 */
internal fun AuthTabIntent.AuthResult.toAuthorizationResponse(): AuthorizationResponse {
	return when (resultCode) {
		AuthTabIntent.RESULT_OK -> {
			resultUri?.let { AuthorizationResponse.from(it.toString()) } ?: AuthorizationResponse.MalformedResponseUriError
		}

		AuthTabIntent.RESULT_CANCELED -> AuthorizationResponse.Cancellation

		AuthTabIntent.RESULT_VERIFICATION_FAILED -> AuthorizationResponse.Error(
			errorType = AuthorizationErrorType.AUTHORIZATION,
			message = "Auth tab redirect verification failed. Make sure the Digital Asset Links of the redirect URI host list this app",
		)

		AuthTabIntent.RESULT_VERIFICATION_TIMED_OUT -> AuthorizationResponse.Error(
			errorType = AuthorizationErrorType.AUTHORIZATION,
			message = "Auth tab redirect verification timed out",
		)

		else -> AuthorizationResponse.Error(
			errorType = AuthorizationErrorType.AUTHORIZATION,
			message = "Auth tab returned an unknown result code ($resultCode)",
		)
	}
}
