package org.heidiverse.connect.sdk.exception

class TokenVerificationException(cause: Throwable?) : AuthorizationException(
	errorType = AuthorizationErrorType.TOKEN_VERIFICATION_HOOK,
	cause = cause,
	errorCode = HeidentityConnectErrorCode.typed(
		AuthorizationErrorType.TOKEN_VERIFICATION_HOOK.errorCode,
		HeidentityConnectErrorCode.OFFSET_AUTHORIZATION_TOKEN_VERIFICATION,
	),
)
