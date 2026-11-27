package org.heidiverse.connect.sdk.extensions.okhttp.common

import kotlinx.serialization.Serializable
import org.heidiverse.connect.sdk.extensions.common.CookieStoreData

@Serializable
data class OkHttpCookieStoreData(
	val cookies: List<SerializableCookie>
) : CookieStoreData
