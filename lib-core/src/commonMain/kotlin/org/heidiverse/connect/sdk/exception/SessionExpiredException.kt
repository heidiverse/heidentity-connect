package org.heidiverse.connect.sdk.exception

import org.heidiverse.connect.sdk.networking.token.TokenErrorResponse

class SessionExpiredException(val errorResponse: TokenErrorResponse) : Exception(), HeidentityConnectErrorCodeException {
	override val errorCode: Int = HeidentityConnectErrorCode.EXCEPTION_SESSION_EXPIRED
}
