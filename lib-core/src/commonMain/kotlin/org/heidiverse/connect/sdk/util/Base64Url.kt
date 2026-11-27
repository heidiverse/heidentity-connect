package org.heidiverse.connect.sdk.util

import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

/**
 * Base64url encoding without padding, as required by both PKCE (RFC 7636) and JOSE (RFC 7515 Appendix C).
 */
@OptIn(ExperimentalEncodingApi::class)
internal val BASE64_URL = Base64.UrlSafe.withPadding(Base64.PaddingOption.ABSENT)
