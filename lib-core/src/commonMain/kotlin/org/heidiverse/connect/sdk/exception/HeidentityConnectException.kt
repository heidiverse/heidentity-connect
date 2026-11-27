package org.heidiverse.connect.sdk.exception

open class HeidentityConnectException(
	open val errorType: HeidentityConnectErrorType,
	message: String? = null,
	cause: Throwable? = null,
	override val errorCode: Int = HeidentityConnectErrorCode.typed(errorType.errorCode, HeidentityConnectErrorCode.OFFSET_GENERIC),
) : Exception(message, cause), HeidentityConnectErrorCodeException {
	// The empty companion object is required to be able to define extension functions on the companion object.
	// This is used in lib-extensions-ktor and lib-extensions-okhttp
	// See:
	// - https://kotlinlang.org/docs/extensions.html#companion-object-extensions
	// - https://youtrack.jetbrains.com/issue/KT-11968
	companion object {}

	init {
		require(errorCode in 1000..99999) { "errorCode must be in range 1000..99999" }
	}
}
