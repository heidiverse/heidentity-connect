package org.heidiverse.connect.sdk.exception

class CancelledLoginException : AuthorizationException(
	AuthorizationErrorType.AUTHORIZATION,
	"The operation has been cancelled.",
	errorCode = HeidentityConnectErrorCode.typed(
		AuthorizationErrorType.AUTHORIZATION.errorCode,
		HeidentityConnectErrorCode.OFFSET_AUTHORIZATION_CANCELLED,
	),
)
