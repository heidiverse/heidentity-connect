package org.heidiverse.connect.sdk.dpop

import kotlin.test.Test
import kotlin.test.assertEquals

class DpopHtuTest {

	@Test
	fun stripsQueryAndFragment() {
		assertEquals(
			"https://server.example.com/token",
			DpopHtu.normalize("https://server.example.com/token?foo=bar&baz=1#section"),
		)
	}

	@Test
	fun keepsPathIncludingTrailingSlash() {
		assertEquals(
			"https://api.example.com/v1/users/",
			DpopHtu.normalize("https://api.example.com/v1/users/"),
		)
	}

	@Test
	fun emptyPathStaysEmpty() {
		assertEquals("https://api.example.com", DpopHtu.normalize("https://api.example.com"))
		assertEquals("https://api.example.com/", DpopHtu.normalize("https://api.example.com/"))
	}

	@Test
	fun lowercasesSchemeAndHost() {
		assertEquals(
			"https://api.example.com/Path",
			DpopHtu.normalize("HTTPS://API.Example.COM/Path"),
		)
	}

	@Test
	fun dropsDefaultPortButKeepsOthers() {
		assertEquals("https://api.example.com/x", DpopHtu.normalize("https://api.example.com:443/x"))
		assertEquals("http://api.example.com/x", DpopHtu.normalize("http://api.example.com:80/x"))
		assertEquals("https://api.example.com:8443/x", DpopHtu.normalize("https://api.example.com:8443/x"))
		// 80 is not the default port for https and must survive
		assertEquals("https://api.example.com:80/x", DpopHtu.normalize("https://api.example.com:80/x"))
	}

	@Test
	fun stripsUserinfo() {
		assertEquals(
			"https://api.example.com/x",
			DpopHtu.normalize("https://user:password@api.example.com/x"),
		)
	}

	@Test
	fun handlesIpv6Literals() {
		assertEquals("http://[::1]:8080/x", DpopHtu.normalize("http://[::1]:8080/x"))
		assertEquals("http://[::1]/x", DpopHtu.normalize("http://[::1]:80/x"))
		assertEquals("http://[::1]", DpopHtu.normalize("http://[::1]"))
	}

	@Test
	fun passesThroughNonAbsoluteUrls() {
		assertEquals("/relative/path", DpopHtu.normalize("/relative/path?q=1"))
	}
}
