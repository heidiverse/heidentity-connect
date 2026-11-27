package org.heidiverse.connect.sdk.extensions.ktor.common

import kotlinx.serialization.Serializable
import org.heidiverse.connect.sdk.extensions.common.CookieStoreData

@Serializable
data class KtorCookieStoreData(
	val cookies: List<CookieWithTimestamp>
) : CookieStoreData
