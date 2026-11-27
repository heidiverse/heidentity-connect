package org.heidiverse.connect.sdk.exception

class NetworkTimeoutException(
	errorType: HeidentityConnectErrorType,
	message: String? = null,
	cause: Throwable? = null,
	platformError: PlatformNetworkError? = null,
	errorCode: Int = HeidentityConnectErrorCode.typed(errorType.errorCode, HeidentityConnectErrorCode.OFFSET_NETWORK_TIMEOUT),
) : NetworkException(errorType, message, cause, platformError, errorCode)
