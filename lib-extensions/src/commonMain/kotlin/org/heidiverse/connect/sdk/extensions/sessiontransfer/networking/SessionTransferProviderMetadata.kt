package org.heidiverse.connect.sdk.extensions.sessiontransfer.networking

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class SessionTransferProviderMetadata(
	@SerialName("stt_create_endpoint")
	val sttCreateEndpoint: String? = null,

	@SerialName("stt_use_endpoint")
	val sttUseEndpoint: String? = null,
)
