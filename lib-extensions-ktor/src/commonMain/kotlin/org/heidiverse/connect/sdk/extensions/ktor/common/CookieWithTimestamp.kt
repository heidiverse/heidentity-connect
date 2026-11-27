package org.heidiverse.connect.sdk.extensions.ktor.common

import io.ktor.http.Cookie
import kotlinx.serialization.Serializable

@Serializable
data class CookieWithTimestamp(val cookie: Cookie, val createdAt: Long)