package org.heidiverse.connect.sdk.exception

interface HeidentityConnectErrorType {
	/** Prefix used for type-aware response error codes (see [HeidentityConnectErrorCode.response]). */
	val errorCode: Int
}
