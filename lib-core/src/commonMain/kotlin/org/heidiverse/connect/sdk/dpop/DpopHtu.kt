package org.heidiverse.connect.sdk.dpop

/**
 * Normalization of the DPoP `htu` claim per
 * [RFC 9449 §4.2](https://datatracker.ietf.org/doc/html/rfc9449#section-4.2): the "HTTP target URI without query and
 * fragment parts". Additionally applies the RFC 3986 §6.2.2 syntax-based normalizations that servers commonly apply
 * when reconstructing the URI they compare against — lowercased scheme and host, and no default port.
 */
internal object DpopHtu {

	private val DEFAULT_PORTS = mapOf("http" to "80", "https" to "443")

	fun normalize(url: String): String {
		val withoutQueryAndFragment = url.substringBefore('#').substringBefore('?')

		val schemeEnd = withoutQueryAndFragment.indexOf("://")
		if (schemeEnd <= 0) {
			// Not an absolute URL. Nothing sensible to normalize, pass it through so the server can reject it rather
			// than silently sending a subtly different value.
			return withoutQueryAndFragment
		}

		val scheme = withoutQueryAndFragment.substring(0, schemeEnd).lowercase()
		val afterScheme = withoutQueryAndFragment.substring(schemeEnd + 3)

		val pathStart = afterScheme.indexOf('/')
		val authority = if (pathStart < 0) afterScheme else afterScheme.substring(0, pathStart)
		val path = if (pathStart < 0) "" else afterScheme.substring(pathStart)

		// Userinfo must not appear in htu
		val hostAndPort = authority.substringAfterLast('@')

		return "$scheme://${normalizeHostAndPort(hostAndPort, scheme)}$path"
	}

	private fun normalizeHostAndPort(hostAndPort: String, scheme: String): String {
		val (host, port) = splitHostAndPort(hostAndPort)
		return if (port == null || port == DEFAULT_PORTS[scheme]) {
			host.lowercase()
		} else {
			"${host.lowercase()}:$port"
		}
	}

	private fun splitHostAndPort(hostAndPort: String): Pair<String, String?> {
		if (hostAndPort.startsWith("[")) {
			// IPv6 literal, e.g. [::1]:8080
			val bracketEnd = hostAndPort.indexOf(']')
			if (bracketEnd < 0) return hostAndPort to null
			val host = hostAndPort.substring(0, bracketEnd + 1)
			val remainder = hostAndPort.substring(bracketEnd + 1)
			return host to remainder.removePrefix(":").takeIf { it.isNotEmpty() }
		}

		val colonIndex = hostAndPort.lastIndexOf(':')
		if (colonIndex < 0) return hostAndPort to null
		return hostAndPort.substring(0, colonIndex) to
			hostAndPort.substring(colonIndex + 1).takeIf { it.isNotEmpty() }
	}
}
