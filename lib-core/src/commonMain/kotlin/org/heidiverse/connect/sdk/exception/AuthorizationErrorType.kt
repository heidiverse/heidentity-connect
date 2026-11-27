package org.heidiverse.connect.sdk.exception

enum class AuthorizationErrorType(
	override val errorCode: Int,
) : HeidentityConnectErrorType {
	GENERAL(HeidentityConnectErrorCode.TYPE_AUTHORIZATION_GENERAL),
	DISCOVERY(HeidentityConnectErrorCode.TYPE_AUTHORIZATION_DISCOVERY),
	AUTHORIZATION(HeidentityConnectErrorCode.TYPE_AUTHORIZATION_FLOW),
	AUTHORIZATION_CODE_EXCHANGE(HeidentityConnectErrorCode.TYPE_AUTHORIZATION_CODE_EXCHANGE),
	TOKEN_REFRESH(HeidentityConnectErrorCode.TYPE_AUTHORIZATION_TOKEN_REFRESH),
	TOKEN_VERIFICATION_HOOK(HeidentityConnectErrorCode.TYPE_AUTHORIZATION_TOKEN_VERIFICATION_HOOK),
}
