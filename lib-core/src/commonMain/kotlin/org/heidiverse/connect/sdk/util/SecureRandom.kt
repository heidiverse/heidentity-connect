package org.heidiverse.connect.sdk.util

/**
 * Generates [size] cryptographically secure random bytes.
 * This must be backed by a CSPRNG on every platform, as the result is used for security relevant
 * values such as the PKCE code verifier, OAuth `state` and OIDC `nonce`.
 */
internal expect fun secureRandomBytes(size: Int): ByteArray
