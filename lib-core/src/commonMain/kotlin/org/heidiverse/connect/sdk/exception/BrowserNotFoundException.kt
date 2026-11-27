package org.heidiverse.connect.sdk.exception

class BrowserNotFoundException : AuthorizationException(
	AuthorizationErrorType.AUTHORIZATION,
	"No matching browser found for handling the authorization request",
	errorCode = HeidentityConnectErrorCode.typed(
		AuthorizationErrorType.AUTHORIZATION.errorCode,
		HeidentityConnectErrorCode.OFFSET_AUTHORIZATION_BROWSER_NOT_FOUND,
	),
)
