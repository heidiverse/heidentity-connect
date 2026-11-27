package org.heidiverse.connect.sdk.exception

class MissingRefreshTokenException : AuthorizationException(
	AuthorizationErrorType.TOKEN_REFRESH,
	"Refresh token is missing for a token refresh",
	errorCode = HeidentityConnectErrorCode.typed(
		AuthorizationErrorType.TOKEN_REFRESH.errorCode,
		HeidentityConnectErrorCode.OFFSET_AUTHORIZATION_MISSING_REFRESH_TOKEN,
	),
)
