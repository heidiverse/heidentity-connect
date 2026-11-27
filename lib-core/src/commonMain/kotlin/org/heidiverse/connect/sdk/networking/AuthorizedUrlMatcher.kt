package org.heidiverse.connect.sdk.networking

import com.eygraber.uri.Url

/**
 * Decides whether a request URL is covered by a set of authorized base URLs.
 *
 * Both URLs are reduced to their origin (`scheme://host:port`) and those are compared for equality, as suggested by
 * RFC 3986 section 6.2.1. The path, query, fragment and any userinfo component are not part of the comparison, so a
 * base URL of `https://api.example.com/api` authorizes every path on `https://api.example.com`.
 *
 * Base URLs should be written without an explicit default port: `https://api.example.com:443` and
 * `https://api.example.com` are different origins here.
 */
object AuthorizedUrlMatcher {

	/**
	 * @return true if [requestUrl] is authorized by at least one of the [authorizedBaseUrls]
	 */
	fun matches(requestUrl: String, authorizedBaseUrls: Set<String>): Boolean {
		val requestOrigin = originOf(requestUrl) ?: return false
		return authorizedBaseUrls.any { originOf(it) == requestOrigin }
	}

	/** The `scheme://host:port` of [url] in lowercase (port `-1` if implicit), or null if it is not an absolute URL. */
	private fun originOf(url: String): String? =
		runCatching { Url.parse(url) }.getOrNull()?.let { "${it.scheme}://${it.host}:${it.port}".lowercase() }

}
