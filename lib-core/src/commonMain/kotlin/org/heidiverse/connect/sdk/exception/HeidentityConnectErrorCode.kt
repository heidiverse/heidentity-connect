package org.heidiverse.connect.sdk.exception

object HeidentityConnectErrorCode {
	/**
	 * Error-type prefixes used to encode response errors as:
	 * `errorTypePrefix + httpStatus`, e.g. token-refresh 401 -> 5401
	 */
	const val TYPE_AUTHORIZATION_GENERAL = 1000
	const val TYPE_AUTHORIZATION_DISCOVERY = 2000
	const val TYPE_AUTHORIZATION_FLOW = 3000
	const val TYPE_AUTHORIZATION_CODE_EXCHANGE = 4000
	const val TYPE_AUTHORIZATION_TOKEN_REFRESH = 5000
	const val TYPE_AUTHORIZATION_TOKEN_VERIFICATION_HOOK = 6000

	const val EXCEPTION_RESPONSE_UNKNOWN_STATUS = 9600
	const val EXCEPTION_UNAUTHORIZED = 9700
	const val EXCEPTION_SESSION_EXPIRED = 9710

	const val OFFSET_GENERIC = 1
	const val OFFSET_AUTHORIZATION = 10
	const val OFFSET_AUTHORIZATION_BROWSER_NOT_FOUND = 11
	const val OFFSET_AUTHORIZATION_CANCELLED = 12
	const val OFFSET_AUTHORIZATION_MALFORMED_RESPONSE_URI = 13
	const val OFFSET_AUTHORIZATION_STATE_MISMATCH = 14
	const val OFFSET_AUTHORIZATION_MISSING_REFRESH_TOKEN = 15
	const val OFFSET_AUTHORIZATION_TOKEN_VERIFICATION = 16
	const val OFFSET_NETWORK = 20
	const val OFFSET_NETWORK_TIMEOUT = 21
	const val OFFSET_DESERIALIZATION = 22
	const val OFFSET_METADATA_NOT_AVAILABLE = 30
	const val OFFSET_METADATA_DESERIALIZATION = 31
	const val OFFSET_METADATA_MISSING_FIELD = 32
	const val OFFSET_DPOP_NO_SUPPORTED_ALGORITHM = 40
	const val OFFSET_DPOP_HARDWARE_BACKING_UNAVAILABLE = 41
	const val OFFSET_DPOP_KEY_GENERATION_FAILED = 42
	const val OFFSET_DPOP_KEY_MISSING = 43
	const val OFFSET_DPOP_SIGNING_FAILED = 44
	const val OFFSET_DPOP_TOKEN_NOT_DPOP_BOUND = 45

	const val OFFSET_BROWSERLESS_APP_NOT_INSTALLED = 50
	const val OFFSET_BROWSERLESS_MALFORMED_URL = 51
	const val OFFSET_BROWSERLESS_UNTRUSTED_REDIRECT_URI = 52

	fun typed(typePrefix: Int, classOffset: Int): Int {
		require(classOffset in 1..99) { "classOffset must be in range 1..99" }
		return typePrefix + classOffset
	}

	fun response(errorTypePrefix: Int, httpStatus: Int): Int {
		return if (httpStatus in 100..599) {
			errorTypePrefix + httpStatus
		} else {
			EXCEPTION_RESPONSE_UNKNOWN_STATUS
		}
	}
}
