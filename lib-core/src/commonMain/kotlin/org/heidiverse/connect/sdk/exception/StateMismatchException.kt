package org.heidiverse.connect.sdk.exception

class StateMismatchException : AuthorizationException(
	AuthorizationErrorType.AUTHORIZATION,
	"Authorization response state does not match request state",
	errorCode = HeidentityConnectErrorCode.typed(
		AuthorizationErrorType.AUTHORIZATION.errorCode,
		HeidentityConnectErrorCode.OFFSET_AUTHORIZATION_STATE_MISMATCH,
	),
)
