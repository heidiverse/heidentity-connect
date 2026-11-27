package org.heidiverse.connect.sdk.extensions.sessiontransfer.networking.token

import kotlinx.serialization.Serializable

@Serializable
data class SessionTransferTokenResponseBody(val token: String)
