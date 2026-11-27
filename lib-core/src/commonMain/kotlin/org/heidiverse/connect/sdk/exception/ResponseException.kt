package org.heidiverse.connect.sdk.exception

class ResponseException(
	errorType: HeidentityConnectErrorType,
	val statusCode: Int,
	message: String? = null,
	cause: Throwable? = null,
	platformError: PlatformNetworkError? = null,
	val headers: Map<String, List<String>> = emptyMap(),
	val body: String? = null,
	errorCode: Int = HeidentityConnectErrorCode.response(errorType.errorCode, statusCode),
) : NetworkException(errorType, message, cause, platformError, errorCode)
