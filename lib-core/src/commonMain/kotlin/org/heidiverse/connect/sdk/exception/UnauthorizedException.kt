package org.heidiverse.connect.sdk.exception

class UnauthorizedException : Exception(), HeidentityConnectErrorCodeException {
	override val errorCode: Int = HeidentityConnectErrorCode.EXCEPTION_UNAUTHORIZED
}
