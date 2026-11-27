package org.heidiverse.connect.sdk.networking

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AuthorizedUrlMatcherTest {

	@Test
	fun exactOriginMatches() {
		assertTrue(AuthorizedUrlMatcher.matches("https://api.example.com", setOf("https://api.example.com")))
	}

	@Test
	fun anyPathOnAuthorizedOriginMatches() {
		val authorizedBaseUrls = setOf("https://api.example.com/api")
		assertTrue(AuthorizedUrlMatcher.matches("https://api.example.com/api/x", authorizedBaseUrls))
		assertTrue(AuthorizedUrlMatcher.matches("https://api.example.com/admin", authorizedBaseUrls))
		assertTrue(AuthorizedUrlMatcher.matches("https://api.example.com/", authorizedBaseUrls))
	}

	@Test
	fun pathOnUnauthorizedOriginDoesNotMatch() {
		assertFalse(AuthorizedUrlMatcher.matches("https://other.example.com/api", setOf("https://api.example.com/api")))
	}

	@Test
	fun hostSuffixAttackDoesNotMatch() {
		assertFalse(AuthorizedUrlMatcher.matches("https://api.example.com.evil.net/x", setOf("https://api.example.com")))
	}

	@Test
	fun userInfoAttackDoesNotMatch() {
		assertFalse(AuthorizedUrlMatcher.matches("https://api.example.com@evil.net/x", setOf("https://api.example.com")))
	}

	@Test
	fun differentSchemeDoesNotMatch() {
		assertFalse(AuthorizedUrlMatcher.matches("http://api.example.com", setOf("https://api.example.com")))
	}

	@Test
	fun differentPortDoesNotMatch() {
		assertFalse(AuthorizedUrlMatcher.matches("https://api.example.com:8443/x", setOf("https://api.example.com/x")))
	}

	@Test
	fun explicitDefaultPortDoesNotMatch() {
		assertFalse(AuthorizedUrlMatcher.matches("https://api.example.com:443/x", setOf("https://api.example.com/x")))
	}

	@Test
	fun caseInsensitiveHostMatches() {
		assertTrue(AuthorizedUrlMatcher.matches("https://API.example.com/x", setOf("https://api.example.com/x")))
	}

	@Test
	fun hostWithTrailingDotDoesNotMatch() {
		assertFalse(AuthorizedUrlMatcher.matches("https://api.example.com./x", setOf("https://api.example.com/x")))
	}

	@Test
	fun emptyAuthorizedSetNeverMatches() {
		assertFalse(AuthorizedUrlMatcher.matches("https://api.example.com/x", emptySet()))
	}

	@Test
	fun unparseableUrlNeverMatches() {
		assertFalse(AuthorizedUrlMatcher.matches("not a url", setOf("https://api.example.com")))
	}

}
