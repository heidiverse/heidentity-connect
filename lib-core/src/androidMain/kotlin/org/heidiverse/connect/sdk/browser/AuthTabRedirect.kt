package org.heidiverse.connect.sdk.browser

import androidx.core.net.toUri

/**
 * The redirect an Auth Tab watches for to complete the authorization flow. Unlike a Custom Tab, an Auth Tab intercepts the
 * redirect itself instead of letting the system dispatch it as a deeplink, so it has to be told upfront what to look for.
 */
internal sealed interface AuthTabRedirect {

	/**
	 * A redirect to a custom, non-HTTP scheme, e.g. `myapp://callback`.
	 */
	data class CustomScheme(val scheme: String) : AuthTabRedirect

	/**
	 * A redirect to an HTTPS URI. Requires the app and the [host] to be associated through Digital Asset Links, otherwise
	 * the Auth Tab reports a failed verification instead of returning the authorization response.
	 */
	data class Https(val host: String, val path: String) : AuthTabRedirect

	companion object {

		/**
		 * @return The redirect to watch for, or null if an Auth Tab cannot intercept [redirectUri] and the authorization
		 * request has to be presented in a Custom Tab instead.
		 */
		fun from(redirectUri: String): AuthTabRedirect? {
			val uri = redirectUri.toUri()
			val scheme = uri.scheme ?: return null

			return when {
				scheme.equals("https", ignoreCase = true) -> {
					val host = uri.host?.takeIf { it.isNotEmpty() } ?: return null
					// An empty path would never match the redirect, so fall back to the host root
					Https(host, uri.path?.takeIf { it.isNotEmpty() } ?: "/")
				}

				// Only HTTPS redirects are verified and intercepted, a plaintext redirect never is
				scheme.equals("http", ignoreCase = true) -> null

				else -> CustomScheme(scheme)
			}
		}

	}

}
