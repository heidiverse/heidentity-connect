package org.heidiverse.connect.sdk.dpop

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * The most recent DPoP nonce per server, as supplied via the `DPoP-Nonce` response header
 * ([RFC 9449 §8](https://datatracker.ietf.org/doc/html/rfc9449#section-8)).
 *
 * Nonces are short-lived, server-supplied and only valid for the server that issued them, so this is in-memory only and
 * never persisted next to the tokens.
 *
 * Keyed by [DpopNonceTarget] *and* origin: the authorization server and a resource server maintain independent nonce
 * spaces, and they can share an origin (a reverse proxy in front of both), in which case one slot would keep feeding
 * each of them the other's nonce.
 */
internal class DpopNonceStore {

	private val mutex = Mutex()
	private val nonces = mutableMapOf<Key, String>()

	suspend fun store(target: DpopNonceTarget, url: String, nonce: String) {
		val key = Key(target, origin(url))
		mutex.withLock { nonces[key] = nonce }
	}

	suspend fun get(target: DpopNonceTarget, url: String): String? {
		val key = Key(target, origin(url))
		return mutex.withLock { nonces[key] }
	}

	suspend fun clear() {
		mutex.withLock { nonces.clear() }
	}

	/** `scheme://host[:port]` of [url]. Reuses the htu normalization so the key is stable across path variations. */
	private fun origin(url: String): String {
		val normalized = DpopHtu.normalize(url)
		val schemeEnd = normalized.indexOf("://")
		if (schemeEnd <= 0) return normalized
		val pathStart = normalized.indexOf('/', startIndex = schemeEnd + 3)
		return if (pathStart < 0) normalized else normalized.substring(0, pathStart)
	}

	private data class Key(val target: DpopNonceTarget, val origin: String)
}

/**
 * Which of the two independent nonce spaces of RFC 9449 a nonce belongs to.
 */
enum class DpopNonceTarget {
	/** The token endpoint (RFC 9449 §8): challenges arrive as `400` with `error=use_dpop_nonce`. */
	AUTHORIZATION_SERVER,

	/** A protected resource (RFC 9449 §9): challenges arrive as `401` with a `DPoP` `WWW-Authenticate` challenge. */
	RESOURCE_SERVER,
}
