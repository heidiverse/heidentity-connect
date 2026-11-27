package org.heidiverse.connect.sdk.extensions.sessiontransfer.networking

import org.heidiverse.connect.sdk.extensions.sessiontransfer.networking.token.SessionTransferTokenRequestBody
import org.heidiverse.connect.sdk.tokenhandling.TokenHandler

interface SessionTransferService {

	suspend fun createSessionTransferToken(
		sttUrl: String,
		tokenHandler: TokenHandler,
		request: SessionTransferTokenRequestBody,
	): String
}
