package org.heidiverse.connect.sdk.extensions.browserless

import org.heidiverse.connect.sdk.exception.HeidentityConnectErrorType

enum class BrowserlessErrorType : HeidentityConnectErrorType {
	AUTHORIZATION;

	override val errorCode: Int = BrowserlessErrorCode.TYPE_AUTHORIZATION
}
