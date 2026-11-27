package org.heidiverse.connect.sdk.exception

open class NetworkException(
	errorType: HeidentityConnectErrorType,
	message: String? = null,
	cause: Throwable? = null,
	val platformError: PlatformNetworkError? = null,
	errorCode: Int = HeidentityConnectErrorCode.typed(errorType.errorCode, HeidentityConnectErrorCode.OFFSET_NETWORK),
) : HeidentityConnectException(errorType, message, cause, errorCode)
