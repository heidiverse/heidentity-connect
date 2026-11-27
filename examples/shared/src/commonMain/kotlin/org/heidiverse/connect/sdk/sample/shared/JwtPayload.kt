package org.heidiverse.connect.sdk.sample.shared

import kotlinx.serialization.json.Json
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

/**
 * Decodes JWT payloads for display in the sample apps. An object rather than an extension function so that it can be
 * called through Swift export as well.
 */
object JwtPayload {

	private val prettyJson = Json {
		prettyPrint = true
		prettyPrintIndent = "  "
	}

	/** JWT segments are base64url without padding, but tolerate the occasional issuer that pads anyway. */
	@OptIn(ExperimentalEncodingApi::class)
	private val BASE64_URL = Base64.UrlSafe.withPadding(Base64.PaddingOption.ABSENT_OPTIONAL)

	/**
	 * The payload of a JWT, pretty printed, or null if this is not a JWT — a refresh token, for instance, is often an
	 * opaque string.
	 *
	 * This only splits the token apart and decodes the middle section. Nothing is verified: not the signature, not the
	 * issuer, not the expiry. Good enough to look at what a token says while developing, and useless as a security
	 * check.
	 */
	@OptIn(ExperimentalEncodingApi::class)
	fun decode(token: String): String? {
		val parts = token.split(".")
		if (parts.size != 3) return null

		return runCatching {
			val payload = BASE64_URL.decode(parts[1])
			val element = Json.parseToJsonElement(payload.decodeToString())
			prettyJson.encodeToString(element)
		}.getOrNull()
	}

}
