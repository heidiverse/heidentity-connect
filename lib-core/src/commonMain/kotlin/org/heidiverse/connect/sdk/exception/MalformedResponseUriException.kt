package org.heidiverse.connect.sdk.exception

class MalformedResponseUriException : AuthorizationException(
	AuthorizationErrorType.AUTHORIZATION,
	"Authorization response URI was malformed",
	errorCode = HeidentityConnectErrorCode.typed(
		AuthorizationErrorType.AUTHORIZATION.errorCode,
		HeidentityConnectErrorCode.OFFSET_AUTHORIZATION_MALFORMED_RESPONSE_URI,
	),
)
