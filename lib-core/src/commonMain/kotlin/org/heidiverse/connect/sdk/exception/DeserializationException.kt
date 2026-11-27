package org.heidiverse.connect.sdk.exception

class DeserializationException(
	errorType: HeidentityConnectErrorType,
	message: String? = null,
	cause: Throwable? = null,
	errorCode: Int = HeidentityConnectErrorCode.typed(errorType.errorCode, HeidentityConnectErrorCode.OFFSET_DESERIALIZATION),
) : NetworkException(errorType, message, cause, errorCode = errorCode)
