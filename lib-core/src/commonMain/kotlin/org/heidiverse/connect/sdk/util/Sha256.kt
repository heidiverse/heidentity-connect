package org.heidiverse.connect.sdk.util

/**
 * SHA-256 digest. Required for the DPoP `ath` claim and the RFC 7638 JWK thumbprint.
 */
internal expect object Sha256 {
	fun digest(bytes: ByteArray): ByteArray
}
