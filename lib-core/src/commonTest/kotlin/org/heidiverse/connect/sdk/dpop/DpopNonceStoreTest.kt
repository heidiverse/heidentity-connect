package org.heidiverse.connect.sdk.dpop

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class DpopNonceStoreTest {

	private val store = DpopNonceStore()

	@Test
	fun returnsNullWhenNothingWasStored() = runTest {
		assertNull(store.get(DpopNonceTarget.RESOURCE_SERVER, "https://api.example.com/x"))
	}

	@Test
	fun isSharedAcrossPathsOfTheSameOrigin() = runTest {
		store.store(DpopNonceTarget.RESOURCE_SERVER, "https://api.example.com/a?q=1", "nonce-1")

		assertEquals("nonce-1", store.get(DpopNonceTarget.RESOURCE_SERVER, "https://api.example.com/b/c"))
	}

	@Test
	fun isolatesDifferentOrigins() = runTest {
		store.store(DpopNonceTarget.RESOURCE_SERVER, "https://a.example.com/x", "nonce-a")
		store.store(DpopNonceTarget.RESOURCE_SERVER, "https://b.example.com/x", "nonce-b")
		store.store(DpopNonceTarget.RESOURCE_SERVER, "https://a.example.com:8443/x", "nonce-a-port")

		assertEquals("nonce-a", store.get(DpopNonceTarget.RESOURCE_SERVER, "https://a.example.com/y"))
		assertEquals("nonce-b", store.get(DpopNonceTarget.RESOURCE_SERVER, "https://b.example.com/y"))
		assertEquals("nonce-a-port", store.get(DpopNonceTarget.RESOURCE_SERVER, "https://a.example.com:8443/y"))
	}

	/**
	 * The authorization server and a resource server keep independent nonce spaces and may sit behind the same origin.
	 * Sharing one slot would keep feeding each of them the other's nonce.
	 */
	@Test
	fun isolatesTheAuthorizationServerFromTheResourceServer() = runTest {
		store.store(DpopNonceTarget.AUTHORIZATION_SERVER, "https://example.com/token", "nonce-as")
		store.store(DpopNonceTarget.RESOURCE_SERVER, "https://example.com/api", "nonce-rs")

		assertEquals("nonce-as", store.get(DpopNonceTarget.AUTHORIZATION_SERVER, "https://example.com/token"))
		assertEquals("nonce-rs", store.get(DpopNonceTarget.RESOURCE_SERVER, "https://example.com/api"))
	}

	@Test
	fun newerNonceReplacesTheOlderOne() = runTest {
		store.store(DpopNonceTarget.AUTHORIZATION_SERVER, "https://example.com/token", "old")
		store.store(DpopNonceTarget.AUTHORIZATION_SERVER, "https://example.com/token", "new")

		assertEquals("new", store.get(DpopNonceTarget.AUTHORIZATION_SERVER, "https://example.com/token"))
	}

	@Test
	fun clearDropsEverything() = runTest {
		store.store(DpopNonceTarget.AUTHORIZATION_SERVER, "https://example.com/token", "nonce")

		store.clear()

		assertNull(store.get(DpopNonceTarget.AUTHORIZATION_SERVER, "https://example.com/token"))
	}
}
