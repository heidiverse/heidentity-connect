package org.heidiverse.connect.sdk.extensions.okhttp.common

import kotlinx.serialization.Serializable
import okhttp3.Cookie

@Serializable
data class SerializableCookie(
	val name: String,
	val value: String,
	val expiresAt: Long,
	val domain: String,
	val path: String,
	val secure: Boolean,
	val httpOnly: Boolean,
	val hostOnly: Boolean,
	val sameSite: String?
)

fun Cookie.asSerializable(): SerializableCookie =
	SerializableCookie(
		name = name,
		value = value,
		expiresAt = expiresAt,
		domain = domain,
		path = path,
		secure = secure,
		httpOnly = httpOnly,
		hostOnly = hostOnly,
		sameSite = sameSite,
	)

fun SerializableCookie.asOkHttp(): Cookie =
	Cookie.Builder()
		.name(name)
		.value(value)
		.expiresAt(expiresAt)
		.apply {
			if (hostOnly) hostOnlyDomain(domain)
			else domain(domain)
		}
		.path(path)
		.apply { if (secure) secure() }
		.apply { if (httpOnly) httpOnly() }
		.apply { sameSite?.let{ sameSite(it) } }
		.build()
