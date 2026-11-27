package org.heidiverse.connect.sdk.sample.shared

import org.heidiverse.connect.sdk.data.AuthTokens
import org.heidiverse.connect.sdk.exception.HeidentityConnectErrorCodeException

data class LoginState(
	val isLoading: Boolean = false,
	val isLoggedIn: Boolean = false,
	val errorMessage: String? = null,
	val tokens: SampleTokens? = null,
)

data class SampleTokens(
	val accessToken: String,
	val idToken: String?,
	val refreshToken: String?,
)

internal fun AuthTokens.toSampleTokens() = SampleTokens(
	accessToken = accessToken,
	idToken = idToken,
	refreshToken = refreshToken,
)

internal fun Throwable.toErrorMessage(): String {
	val description = message ?: this::class.simpleName ?: "Unknown error"
	val errorCode = (this as? HeidentityConnectErrorCodeException)?.errorCode
	return if (errorCode != null) "Error $errorCode: $description" else description
}
