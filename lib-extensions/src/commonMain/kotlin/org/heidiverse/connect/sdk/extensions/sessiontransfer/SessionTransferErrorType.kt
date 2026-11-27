package org.heidiverse.connect.sdk.extensions.sessiontransfer

import org.heidiverse.connect.sdk.exception.HeidentityConnectErrorType

enum class SessionTransferErrorType : HeidentityConnectErrorType {
	SESSION_TRANSFER;

	override val errorCode: Int = SessionTransferErrorCode.TYPE_SESSION_TRANSFER
}
